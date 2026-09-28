package com.ridesaathi.app.feature.places

import android.os.Handler
import android.os.Looper
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.domain.model.PlaceCandidate
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.domain.search.DestinationResolver
import com.ridesaathi.app.domain.search.SearchBoundary
import com.ridesaathi.app.localization.searchFailureKey
import com.ridesaathi.app.navigation.AppScreen

internal class PlaceEditorController(private val app: AppSession) {
    val state = PlaceEditorUiState()
    var returnScreen = AppScreen.Settings
        private set

    private var searchGeneration = 0
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var searchThread: Thread? = null
    private var cancelAddressLocation: (() -> Unit)? = null

    fun onAction(action: PlaceEditorAction) {
        when (action) {
            is PlaceEditorAction.Rename -> state.draftName = action.name
            is PlaceEditorAction.Query -> {
                state.searchQuery = action.query
                state.searchResults = emptyList()
                app.message = ""
                scheduleAddressSearch()
            }

            is PlaceEditorAction.SelectAddress -> {
                cancelAddressSearch()
                state.draftPosition = action.candidate
                state.draftAddress = action.candidate.address
                state.searchQuery = action.candidate.address
                state.searchResults = emptyList()
                app.message = ""
                state.pickingAddress = false
            }

            PlaceEditorAction.ChangeAddress -> {
                state.searchQuery = state.draftAddress
                state.searchResults = emptyList()
                app.message = ""
                state.pickingAddress = true
            }

            PlaceEditorAction.Search -> searchAddress()
            PlaceEditorAction.Save -> savePlace()
            PlaceEditorAction.AskDelete -> state.showDeleteConfirmation = true
            PlaceEditorAction.DismissDelete -> state.showDeleteConfirmation = false
            PlaceEditorAction.Delete -> {
                state.showDeleteConfirmation = false; deletePlace()
            }
        }
    }

    fun openEditor(place: SavedPlace?, home: Boolean): Unit = with(app) {
        returnScreen = if (screen == AppScreen.Onboarding) AppScreen.Onboarding else AppScreen.Settings
        voice.stopListening()
        state.showDeleteConfirmation = false
        state.pendingHome = home
        state.editingId = place?.id
        state.draftName = if (home) word("home") else place?.name.orEmpty()
        state.pickingAddress = place == null
        state.draftAddress = place?.address.orEmpty()
        state.draftPosition = place?.let { PlaceCandidate(it.address, it.latitude, it.longitude) }
        state.searchQuery = place?.address.orEmpty()
        state.searchResults = emptyList()
        cancelAddressSearch()
        message = ""
        screen = AppScreen.PlaceEditor
    }

    fun cancelAddressSearch(): Unit = with(app) {
        searchGeneration++
        cancelAddressLocation?.invoke()
        cancelAddressLocation = null
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        pendingSearch = null
        searchThread?.interrupt()
        searchThread = null
        state.searchPending = false
        state.searching = false
    }

    fun scheduleAddressSearch(): Unit = with(app) {
        cancelAddressSearch()
        if (state.searchQuery.trim().length < 3) return
        state.searchPending = true
        pendingSearch = Runnable { searchAddress() }.also {
            searchHandler.postDelayed(it, 500)
        }
    }

    fun searchAddress(): Unit = with(app) {
        cancelAddressSearch()
        val query = state.searchQuery.trim()
        if (query.length < 3 || screen != AppScreen.PlaceEditor || isDestroyed) return
        val generation = ++searchGeneration
        state.searching = true
        state.searchResults = emptyList()
        message = ""
        val language = locale().toLanguageTag()
        cancelAddressLocation = dependencies.destinationLocationLookup { center ->
            if (generation != searchGeneration || isDestroyed || screen != AppScreen.PlaceEditor) return@destinationLocationLookup
            if (center == null || !SearchBoundary.valid(center)) {
                state.searching = false
                message = word("searchLocationRequired")
                return@destinationLocationLookup
            }
            val provider = dependencies.searchProvider(center)
            searchThread = Thread {
                val result =
                    runCatching { SearchBoundary.filter(center, provider.search(query, language)) }
                runOnUiThread {
                    if (generation != searchGeneration || isDestroyed || screen != AppScreen.PlaceEditor) return@runOnUiThread
                    searchThread = null
                    state.searching = false
                    result.onSuccess {
                        state.searchResults = it
                        if (it.isEmpty()) message = word("noResults")
                    }.onFailure { message = word(searchFailureKey(it)) }
                }
            }.also { it.start() }
        }
    }

    fun savePlace(): Unit = with(app) {
        val point = state.draftPosition ?: run { message = word("invalidPlace"); return }
        val name = state.draftName.trim()
        if (name.isBlank() || state.draftAddress.isBlank()) {
            message = word("invalidPlace"); return
        }
        val old = places.firstOrNull { it.id == state.editingId }
        val saved = SavedPlace(
            id = old?.id ?: java.util.UUID.randomUUID().toString(),
            name = name, aliases = old?.aliases.orEmpty(), address = state.draftAddress,
            latitude = point.latitude, longitude = point.longitude, isHome = state.pendingHome
        )
        if (DestinationResolver.conflicts(saved, places)) {
            message = word("duplicate"); return
        }
        cancelAddressSearch()
        places = places.filterNot { it.id == state.editingId } + saved
        store.savePlaces(places)
        message = ""
        screen = returnScreen
    }

    fun deletePlace(): Unit = with(app) {
        if (state.pendingHome) {
            message = word("replaceHome"); return
        }
        cancelAddressSearch()
        places = places.filterNot { it.id == state.editingId }
        store.savePlaces(places)
        message = ""
        screen = returnScreen
    }
}
