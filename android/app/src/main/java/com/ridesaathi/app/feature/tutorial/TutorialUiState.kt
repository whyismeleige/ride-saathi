package com.ridesaathi.app.feature.tutorial

import androidx.compose.runtime.*
import com.ridesaathi.app.navigation.AppScreen

/** Observable state for the tutorial feature; asynchronous handles stay in its controller. */
@Stable
internal class TutorialUiState {
    var tutorialMode by mutableStateOf(TutorialMode.Intro)
    var tutorialReturnScreen by mutableStateOf(AppScreen.Onboarding)
    var tutorialStep by mutableIntStateOf(0)
}
