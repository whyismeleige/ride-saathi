package com.ridesaathi.app.feature.onboarding

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.feature.tutorial.TutorialMode
import com.ridesaathi.app.navigation.AppScreen

internal enum class OnboardingStep(val titleKey: String, val hintKey: String, val icon: String) {
    Language("language", "onboardingLanguageHint", "language"),
    Introduction("watchIntro", "setupHint", "mic"),
    Name("onboardingNameTitle", "onboardingNameHint", "person"),
    Home("onboardingHomeTitle", "onboardingHomeHint", "home"),
    Places("onboardingPlacesTitle", "onboardingPlacesHint", "pin")
}

internal class OnboardingController(private val app: AppSession) {
    private val restored = app.activity.savedStateRegistry.consumeRestoredStateForKey("onboarding")
    var step by mutableStateOf(
        OnboardingStep.entries.getOrNull(restored?.getInt("step") ?: -1)
            ?: if (app.profile.completed) OnboardingStep.Home else OnboardingStep.Language
    )
        private set
    var introPage by mutableIntStateOf(restored?.getInt("introPage") ?: 0)
        private set

    init {
        app.activity.savedStateRegistry.registerSavedStateProvider("onboarding") {
            Bundle().apply {
                putInt("step", step.ordinal)
                putInt("introPage", introPage)
            }
        }
    }

    fun updateName(name: String) = with(app) {
        profile = profile.copy(name = name)
        store.saveProfile(profile)
        message = ""
    }

    fun back() = with(app) {
        stopPrompt()
        message = ""
        if (step == OnboardingStep.Introduction && introPage > 0) introPage--
        else if (step.ordinal > 0) step = OnboardingStep.entries[step.ordinal - 1]
    }

    fun next() = with(app) {
        stopPrompt()
        message = ""
        when (step) {
            OnboardingStep.Language -> step = OnboardingStep.Introduction
            OnboardingStep.Introduction -> {
                val slides = tutorial.introSlides() + tutorial.fullTutorialSlides()
                if (introPage < slides.lastIndex) introPage++
                else {
                    profile = profile.copy(introSeen = true, tutorialSeen = true)
                    store.saveProfile(profile)
                    step = OnboardingStep.Name
                }
            }
            OnboardingStep.Name -> {
                if (profile.name.isBlank()) message = word("name")
                else step = OnboardingStep.Home
            }
            OnboardingStep.Home -> {
                if (places.any { it.isHome }) step = OnboardingStep.Places
                else editor.openEditor(null, true)
            }
            OnboardingStep.Places -> finish()
        }
    }

    fun finish() = with(app) {
        when {
            profile.name.isBlank() -> { step = OnboardingStep.Name; message = word("name") }
            places.none { it.isHome } -> { step = OnboardingStep.Home; message = word("homeRequired") }
            else -> {
                profile = profile.copy(completed = true)
                store.saveProfile(profile)
                message = ""
                if (profile.tutorialSeen) screen = AppScreen.Home
                else tutorial.openTutorial(TutorialMode.Full, AppScreen.Home)
            }
        }
    }
}
