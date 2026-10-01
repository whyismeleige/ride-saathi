package com.ridesaathi.app.feature.destination

import androidx.compose.runtime.*
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.domain.search.DestinationChoices
import com.ridesaathi.app.domain.search.DestinationQuery
import com.ridesaathi.app.domain.search.SearchBoundary
import com.ridesaathi.app.domain.search.SearchChoice
import com.ridesaathi.app.domain.search.SpeechText
import com.ridesaathi.app.domain.search.VoiceCommands
import com.ridesaathi.app.domain.search.VoiceDecision
import com.ridesaathi.app.localization.searchFailureKey
import com.ridesaathi.app.navigation.AppScreen

internal class DestinationSearchController(private val app: AppSession) {
    var destinationSearch by mutableStateOf<DestinationSearchState?>(null)
    private var destinationSearchGeneration = 0
    private var destinationSearchThread: Thread? = null
    private var cancelSearchLocation: (() -> Unit)? = null
    var searchSelection = false

    fun onAction(action: DestinationSearchAction) {
        when (action) {
            DestinationSearchAction.More -> moreSearchChoices()
            DestinationSearchAction.Previous -> previousSearchChoices()
            DestinationSearchAction.Again -> editDestinationQuery()
            is DestinationSearchAction.EditQuery -> {
                app.voice.stopListening()
                app.stopPrompt()
                destinationSearch = destinationSearch?.copy(query = action.query, error = null)
            }

            is DestinationSearchAction.Select -> chooseSearchResult(action.index)
            DestinationSearchAction.Search -> destinationSearch?.let {
                beginDestinationSearch(
                    it.query,
                    extractPhrase = false
                )
            }
        }
    }

    fun cancelDestinationSearch(clear: Boolean = true): Unit = with(app) {
        destinationSearchGeneration++
        destinationSearchThread?.interrupt()
        destinationSearchThread = null
        cancelSearchLocation?.invoke()
        cancelSearchLocation = null
        stopPrompt()
        destinationSearch = if (clear) null else destinationSearch?.copy(loading = false)
    }

    fun beginDestinationSearch(raw: String, extractPhrase: Boolean = true): Unit = with(app) {
        cancelDestinationSearch()
        shared.cancelSharedLocation()
        voice.stopListening()
        searchSelection = false
        ride.state.selected = null
        message = ""
        screen = AppScreen.DestinationSearch
        val query = if (extractPhrase) DestinationQuery.extract(raw) else raw.trim()
            .takeIf { it.length >= 3 }
        if (query == null) {
            destinationSearch =
                DestinationSearchState(query = raw, editing = true, error = "searchClearer")
            speak(word("searchClearer"))
            return
        }
        destinationSearch = DestinationSearchState(query = query, loading = true)
        speak("${word("searchingDestination")} $query")
        val generation = destinationSearchGeneration
        val language = profile.language
        cancelSearchLocation = dependencies.destinationLocationLookup { location ->
            if (generation != destinationSearchGeneration || screen != AppScreen.DestinationSearch || isDestroyed) return@destinationLocationLookup
            if (location == null || !SearchBoundary.valid(location)) {
                destinationSearch = destinationSearch?.copy(
                    loading = false,
                    error = "searchLocationRequired",
                    retryable = true
                )
                speak(word("searchLocationRequired"))
                return@destinationLocationLookup
            }
            val provider = dependencies.destinationSearchProvider(location)
            destinationSearchThread = Thread {
                val result = runCatching {
                    SearchBoundary.filter(
                        location,
                        provider.search(query, language)
                    )
                }
                runOnUiThread {
                    if (generation != destinationSearchGeneration || screen != AppScreen.DestinationSearch || isDestroyed) return@runOnUiThread
                    destinationSearchThread = null
                    result.onSuccess { candidates ->
                        destinationSearch = destinationSearch?.copy(
                            loading = false, candidates = candidates,
                            error = if (candidates.isEmpty()) "searchNoResults" else null
                        )
                        if (candidates.isEmpty()) speak(word("searchNoResults")) else announceSearchChoices()
                    }.onFailure { error ->
                        val key = searchFailureKey(error)
                        destinationSearch = destinationSearch?.copy(
                            loading = false, error = key,
                            retryable = key in listOf(
                                "searchUnavailable",
                                "offline",
                                "searchLocationRequired"
                            )
                        )
                        speak(word(key))
                    }
                }
            }.also { it.start() }
        }
    }

    fun announceSearchChoices(): Unit = with(app) {
        val state = destinationSearch ?: return
        if (screen != AppScreen.DestinationSearch || state.editing || state.loading || state.visible.isEmpty()) return
        voice.stopListening()
        message = ""
        val generation = destinationSearchGeneration
        val prompt = buildString {
            append(word("searchChoose")).append(". ")
            state.visible.forEachIndexed { index, candidate ->
                append("${index + 1}. ${SpeechText.addressSummary(candidate.address)}. ")
            }
            append(word("searchChoiceHint"))
            if (state.hasMore) append(". ").append(word("searchMoreHint"))
        }
        speak(prompt) {
            if (generation == destinationSearchGeneration && screen == AppScreen.DestinationSearch &&
                destinationSearch == state && permissions.hasMicrophone()
            ) voice.startListening()
        }
    }

    fun moreSearchChoices(): Unit = with(app) {
        val state = destinationSearch ?: return
        voice.stopListening()
        stopPrompt()
        if (state.hasMore) {
            destinationSearch = state.copy(page = state.page + 1)
            announceSearchChoices()
        } else {
            message = word("searchNoMore"); speak(message)
        }
    }

    fun editDestinationQuery(): Unit = with(app) {
        cancelDestinationSearch(clear = false)
        voice.stopListening()
        message = ""
        destinationSearch = destinationSearch?.copy(editing = true, error = null)
        val generation = destinationSearchGeneration
        speak(word("searchClearer")) {
            if (generation == destinationSearchGeneration && screen == AppScreen.DestinationSearch &&
                destinationSearch?.editing == true && permissions.hasMicrophone()
            ) voice.startListening()
        }
    }

    fun previousSearchChoices(): Unit = with(app) {
        val state = destinationSearch ?: return
        destinationSearch = state.copy(page = (state.page - 1).coerceAtLeast(0))
        announceSearchChoices()
    }

    fun chooseSearchResult(index: Int): Unit = with(app) {
        val candidate = destinationSearch?.visible?.getOrNull(index) ?: return
        ride.choose(
            SavedPlace(
                "searched-destination", candidate.address.substringBefore(','), emptyList(),
                candidate.address, candidate.latitude, candidate.longitude
            ), fromSearch = true
        )
    }

    fun handleSearchSpeech(raw: String, interpretedQuery: String? = null): Unit = with(app) {
        val state = destinationSearch ?: return
        val choice =
            DestinationChoices.parse(raw, if (state.editing) emptyList() else state.visible)
        if (choice == SearchChoice.Cancel) {
            ride.cancelRide(); return
        }
        if (choice == SearchChoice.Unknown) DestinationQuery.replacement(raw)?.let { query ->
            voice.resolveDestination(query, queryIsExtracted = true)
            return
        }
        if (state.editing) {
            if (VoiceCommands.decision(raw) == VoiceDecision.No) {
                message = word("searchClearer"); return
            }
            voice.resolveDestination(raw, interpretedQuery = interpretedQuery)
            return
        }
        when (choice) {
            is SearchChoice.Select -> chooseSearchResult(choice.index)
            SearchChoice.More -> moreSearchChoices()
            SearchChoice.Previous -> previousSearchChoices()
            SearchChoice.Again -> editDestinationQuery()
            SearchChoice.Repeat -> if (state.visible.isEmpty()) speak(
                word(
                    state.error ?: "searchClearer"
                )
            ) else announceSearchChoices()

            else -> {
                message = word("searchChoiceUnclear"); speak(message)
            }
        }
    }

    fun returnToChoices(): Unit = with(app) {
        if (searchSelection && destinationSearch != null) {
            ride.cancelLocation()
            voice.stopListening()
            stopPrompt()
            ride.state.selected = null
            searchSelection = false
            message = ""
            screen = AppScreen.DestinationSearch
            announceSearchChoices()
        } else ride.cancelRide()
    }
}
