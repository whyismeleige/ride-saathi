package com.ridesaathi.app.feature.ride

import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.location.PickupLocationLookup
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.domain.search.SpeechText
import com.ridesaathi.app.navigation.AppScreen

internal class RideController(private val app: AppSession) {
    val state = RideUiState()
    private val pickup = PickupLocationLookup(app.activity)

    fun choose(place: SavedPlace, fromSearch: Boolean = false): Unit = with(app) {
        destination.cancelDestinationSearch(clear = !fromSearch)
        destination.searchSelection = fromSearch
        shared.cancelSharedLocation()
        voice.stopListening()
        state.selected = place
        state.choices = emptyList()
        screen = AppScreen.RideConfirmation
        message = ""
        val name = if (place.isHome) word("home") else SpeechText.addressSummary(place.name)
        val address = SpeechText.addressSummary(place.address)
        speak("${word("confirm")} $name. $address")
    }

    fun cancelRide(): Unit = with(app) {
        destination.cancelDestinationSearch()
        destination.searchSelection = false
        shared.cancelSharedLocation()
        voice.stopListening()
        speechOutput.stop()
        cancelLocation()
        state.selected = null
        message = ""
        screen = AppScreen.Home
    }

    fun confirmRide(): Unit = with(app) {
        if (state.handoffInProgress || screen != AppScreen.RideConfirmation) return
        voice.stopListening()
        speechOutput.stop()
        val place = state.selected ?: return
        if (place.address.isBlank()) {
            message = word("invalidPlace"); return
        }
        if (!uberInstalled()) {
            message = word("uberInstall"); return
        }
        state.handoffInProgress = true
        message = word("working")
        permissions.requestPickup { granted ->
            if (state.handoffInProgress && screen == AppScreen.RideConfirmation && state.selected != null) {
                if (granted) fetchLocation()
                else {
                    state.handoffInProgress = false; message =
                        word("locationDenied"); speak(message)
                }
            }
        }
    }

    fun cancelLocation() {
        pickup.cancel()
        state.handoffInProgress = false
    }

    private fun reportLocationUnavailable(): Unit = with(app) {
        state.handoffInProgress = false
        message = word("locationUnavailable")
        speak(message)
    }

    private fun fetchLocation(): Unit = with(app) {
        if (!state.handoffInProgress || screen != AppScreen.RideConfirmation) return
        pickup.lookup(onSuccess = { location ->
            if (state.handoffInProgress && !isDestroyed && screen == AppScreen.RideConfirmation) {
                val place = state.selected
                if (place == null) reportLocationUnavailable()
                else {
                    if (uber.launch(place, location.latitude, location.longitude)) {
                        message = ""
                        state.selected = null
                        destination.cancelDestinationSearch()
                        destination.searchSelection = false
                        screen = AppScreen.Home
                    } else message = word("handoffFailed")
                    state.handoffInProgress = false
                }
            }
        }, onFailure = { failure ->
            if (!isDestroyed) {
                if (failure == PickupLocationLookup.Failure.PermissionDenied) {
                    state.handoffInProgress = false
                    message = word("locationDenied")
                } else reportLocationUnavailable()
            }
        })
    }
}
