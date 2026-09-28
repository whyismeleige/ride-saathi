package com.ridesaathi.app.feature.sharedlocation

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.data.sharedlocation.SharedLocationResolver
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.domain.model.SharedDestination
import com.ridesaathi.app.domain.model.SharedLocation
import com.ridesaathi.app.domain.search.SearchBoundary
import com.ridesaathi.app.domain.sharedlocation.SharedLocationParser
import com.ridesaathi.app.localization.searchFailureKey
import com.ridesaathi.app.navigation.AppScreen

internal class SharedLocationController(private val app: AppSession) {
    val state = SharedLocationUiState()

    private var sharedLocationGeneration = 0
    private var sharedLocationThread: Thread? = null
    private val sharedLocationHandler = Handler(Looper.getMainLooper())
    var sharedLocationResolver = SharedLocationResolver()

    private var cancelSharedSearchLocation: (() -> Unit)? = null

    fun handleSharedLocation(intent: Intent?): Unit = with(app) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        if (destination.destinationSearch != null) ride.cancelRide()
        cancelSharedLocation()
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)
                ?.toString()
        if (SharedLocationParser.supportedUris(sharedText.orEmpty()).isEmpty()) {
            message = word("invalidSharedLocation")
            return
        }
        if (!profile.completed || places.none { it.isHome }) {
            message = word("sharedLocationSetup")
            return
        }
        editor.cancelAddressSearch()
        ride.cancelRide()
        val location = SharedLocationParser.parse(sharedText.orEmpty())
        if (location != null) {
            showSharedLocation(location)
            return
        }
        state.resolvingSharedLocation = true
        message = word("resolvingSharedLocation")
        val generation = sharedLocationGeneration
        val language = profile.language
        sharedLocationThread = Thread {
            val result = runCatching { sharedLocationResolver.resolve(sharedText.orEmpty()) }
            sharedLocationHandler.post {
                if (generation != sharedLocationGeneration || isDestroyed) return@post
                sharedLocationThread = null
                result.onSuccess { destination ->
                    when (destination) {
                        is SharedDestination.Coordinates -> {
                            state.resolvingSharedLocation = false
                            showSharedLocation(destination.location)
                        }

                        is SharedDestination.Address -> searchSharedAddress(
                            destination,
                            generation,
                            language
                        )

                        null -> {
                            state.resolvingSharedLocation = false
                            message = word("sharedLocationUnresolved")
                        }
                    }
                }.onFailure {
                    state.resolvingSharedLocation = false
                    message = word("sharedLocationOffline")
                }
            }
        }.also { it.start() }
    }

    fun searchSharedAddress(
        destination: SharedDestination.Address,
        generation: Int,
        language: String
    ): Unit = with(app) {
        cancelSharedSearchLocation = dependencies.destinationLocationLookup { center ->
            if (generation != sharedLocationGeneration || isDestroyed) return@destinationLocationLookup
            if (center == null || !SearchBoundary.valid(center)) {
                state.resolvingSharedLocation = false
                message = word("searchLocationRequired")
                return@destinationLocationLookup
            }
            val provider = dependencies.searchProvider(center)
            sharedLocationThread = Thread {
                val result = runCatching {
                    SearchBoundary.filter(
                        center,
                        provider.search(destination.query, language)
                    )
                }
                sharedLocationHandler.post {
                    if (generation != sharedLocationGeneration || isDestroyed) return@post
                    sharedLocationThread = null
                    state.resolvingSharedLocation = false
                    result.onSuccess { matches ->
                        if (matches.isEmpty()) message = word("searchNoResults")
                        else {
                            state.sharedAddress = destination.sharedAddress
                            ride.state.choices = matches.mapIndexed { index, place ->
                                SavedPlace(
                                    "shared-location-$index",
                                    place.address.substringBefore(','),
                                    emptyList(),
                                    place.address,
                                    place.latitude,
                                    place.longitude
                                )
                            }
                            screen = AppScreen.SharedChoices
                            message = ""
                            speak(word("selectSharedLocation"))
                        }
                    }.onFailure { message = word(searchFailureKey(it)) }
                }
            }.also { it.start() }
        }
    }

    fun cancelSharedLocation(): Unit = with(app) {
        sharedLocationGeneration++
        cancelSharedSearchLocation?.invoke()
        cancelSharedSearchLocation = null
        sharedLocationThread?.interrupt()
        sharedLocationThread = null
        sharedLocationHandler.removeCallbacksAndMessages(null)
        if (state.resolvingSharedLocation) message = ""
        state.resolvingSharedLocation = false
    }

    fun showSharedLocation(location: SharedLocation): Unit = with(app) {
        val coordinates = SharedLocationParser.formattedCoordinates(location)
        ride.choose(
            SavedPlace(
                id = "shared-location",
                name = word("sharedLocation"),
                aliases = emptyList(),
                address = location.label ?: coordinates,
                latitude = location.latitude,
                longitude = location.longitude
            )
        )
    }
}
