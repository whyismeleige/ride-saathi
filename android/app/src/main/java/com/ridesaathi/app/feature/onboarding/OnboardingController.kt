package com.ridesaathi.app.feature.onboarding

import com.ridesaathi.app.AppSession
import com.ridesaathi.app.feature.tutorial.TutorialMode
import com.ridesaathi.app.navigation.AppScreen

internal class OnboardingController(private val app: AppSession) {
    fun updateName(name: String) = with(app) {
        profile = profile.copy(name = name)
        store.saveProfile(profile)
    }

    fun finish() = with(app) {
        if (profile.name.isBlank()) message = word("name")
        else {
            profile = profile.copy(completed = true)
            store.saveProfile(profile)
            message = ""
            if (profile.tutorialSeen) screen = AppScreen.Home
            else tutorial.openTutorial(TutorialMode.Full, AppScreen.Home)
        }
    }
}
