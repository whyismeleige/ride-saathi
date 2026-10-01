package com.ridesaathi.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ridesaathi.app.core.deeplink.UberDeepLinkLauncher
import com.ridesaathi.app.core.permissions.AppPermissions
import com.ridesaathi.app.core.speech.TextToSpeechController
import com.ridesaathi.app.data.local.LocalStore
import com.ridesaathi.app.feature.destination.DestinationSearchController
import com.ridesaathi.app.feature.home.VoiceController
import com.ridesaathi.app.feature.onboarding.OnboardingController
import com.ridesaathi.app.feature.places.PlaceEditorController
import com.ridesaathi.app.feature.ride.RideController
import com.ridesaathi.app.feature.sharedlocation.SharedLocationController
import com.ridesaathi.app.feature.tutorial.TutorialController
import com.ridesaathi.app.localization.Words
import com.ridesaathi.app.localization.languageLocale
import com.ridesaathi.app.navigation.AppScreen
import com.ridesaathi.app.navigation.initialScreen

/** Activity-lifetime composition root. Recreation intentionally abandons unfinished rides. */
internal class AppSession(val activity: ComponentActivity) : DefaultLifecycleObserver {
    val store = LocalStore(activity)
    var profile by mutableStateOf(store.profile())
    var places by mutableStateOf(store.places())
    var screen by mutableStateOf(initialScreen(profile, places))
    var message by mutableStateOf("")
    val mapEndpoint = store.mapEndpoint()
    var foreground = false
    var isDestroyed = false
        private set
    var intent: Intent? = null
        private set
    val permissions = AppPermissions(activity)
    val dependencies = AppDependencies(permissions)
    val speechOutput =
        TextToSpeechController(
            activity, { locale() }, { foreground && !isDestroyed }, BuildConfig.API_BASE_URL
        )
    val uber = UberDeepLinkLauncher(activity)
    val onboarding = OnboardingController(this)
    val editor = PlaceEditorController(this)
    val destination = DestinationSearchController(this)
    val shared = SharedLocationController(this)
    val tutorial = TutorialController(this)
    val ride = RideController(this)
    val voice = VoiceController(this)

    init {
        speechOutput.onSpeakingChanged = { voice.state.speaking = it }
        speechOutput.onInterrupted = { voice.endSession() }
        activity.lifecycle.addObserver(this)
    }

    fun acceptIntent(value: Intent?) {
        intent = value
        shared.handleSharedLocation(value)
    }

    fun word(key: String) = Words.get(profile.language, key)
    fun locale() = languageLocale(profile.language)
    fun speak(value: String, after: (() -> Unit)? = null) {
        voice.prepareForPrompt()
        speechOutput.speak(value, after)
    }
    fun stopPrompt() = speechOutput.stop()
    fun runOnUiThread(action: () -> Unit) = activity.runOnUiThread(action)
    fun hasSearchLocationPermission() = permissions.hasLocation()
    fun openLocationSettings() = permissions.openLocationSettings()
    fun openAppSettings() = permissions.openAppSettings()
    fun uberInstalled() = uber.isInstalled()
    fun openStore() {
        if (!uber.openStore()) message = word("storeUnavailable")
    }

    override fun onResume(owner: LifecycleOwner) {
        foreground = true
        voice.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        foreground = false
        if (destination.destinationSearch?.loading == true && !permissions.searchPermissionInFlight) {
            destination.cancelDestinationSearch(clear = false)
            destination.destinationSearch =
                destination.destinationSearch?.copy(error = "searchInterrupted", retryable = true)
        }
        voice.endSession()
        speechOutput.stop()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!permissions.searchPermissionInFlight) {
            shared.cancelSharedLocation()
            editor.cancelAddressSearch()
        }
        if (ride.state.handoffInProgress) {
            ride.cancelLocation(); message = word("rideInterrupted")
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        isDestroyed = true
        destination.cancelDestinationSearch()
        shared.cancelSharedLocation()
        editor.cancelAddressSearch()
        ride.cancelLocation()
        voice.close()
        speechOutput.close()
        permissions.close()
        activity.lifecycle.removeObserver(this)
    }

    fun navigateBack() {
        shared.cancelSharedLocation()
        when (screen) {
            AppScreen.Onboarding -> onboarding.back()
            AppScreen.RideConfirmation -> destination.returnToChoices()
            AppScreen.Clarification, AppScreen.SharedChoices, AppScreen.DestinationSearch -> ride.cancelRide()
            AppScreen.Tutorial -> tutorial.finishTutorial()
            AppScreen.PlaceEditor -> {
                editor.cancelAddressSearch()
                if (editor.state.pickingAddress && editor.state.draftPosition != null) {
                    editor.state.pickingAddress = false
                    editor.state.searchQuery = editor.state.draftAddress
                    editor.state.searchResults = emptyList()
                } else screen = editor.returnScreen
            }

            AppScreen.Settings -> screen = AppScreen.Home
            else -> Unit
        }
        message = ""
    }

    fun selectLanguage(code: String) {
        voice.endSession()
        profile = profile.copy(language = code)
        store.saveProfile(profile)
        speechOutput.updateLanguage()
        message = ""
    }
}
