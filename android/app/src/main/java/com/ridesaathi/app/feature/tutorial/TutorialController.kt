package com.ridesaathi.app.feature.tutorial

import com.ridesaathi.app.AppSession
import com.ridesaathi.app.navigation.AppScreen

internal class TutorialController(private val app: AppSession) {
    val state = TutorialUiState()

    data class TutorialSlide(val icon: String, val titleKey: String, val bodyKey: String)

    fun introSlides(): List<TutorialSlide> = listOf(
        TutorialSlide("home", "introFamilyTitle", "introFamilyBody"),
        TutorialSlide("pin", "introPlacesTitle", "introPlacesBody")
    )

    fun fullTutorialSlides(): List<TutorialSlide> = listOf(
        TutorialSlide("mic", "tutorialSpeakTitle", "tutorialSpeakBody"),
        TutorialSlide("check", "tutorialConfirmTitle", "tutorialConfirmBody"),
        TutorialSlide("pin", "tutorialPickupTitle", "tutorialPickupBody"),
        TutorialSlide("arrow", "tutorialUberTitle", "tutorialUberBody")
    )

    fun tutorialSlides(): List<TutorialSlide> =
        if (state.tutorialMode == TutorialMode.Intro) introSlides() else fullTutorialSlides()

    fun openTutorial(mode: TutorialMode, returnScreen: AppScreen): Unit = with(app) {
        voice.stopListening()
        stopPrompt()
        state.tutorialMode = mode
        state.tutorialReturnScreen = returnScreen
        state.tutorialStep = 0
        message = ""
        screen = AppScreen.Tutorial
    }

    fun finishTutorial(): Unit = with(app) {
        stopPrompt()
        profile = if (state.tutorialMode == TutorialMode.Intro) profile.copy(introSeen = true)
        else profile.copy(introSeen = true, tutorialSeen = true)
        store.saveProfile(profile)
        state.tutorialStep = 0
        message = ""
        screen = state.tutorialReturnScreen
    }
}
