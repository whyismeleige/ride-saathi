package com.ridesaathi.app.navigation

import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScreenTest {
    private val home = SavedPlace(
        name = "Home",
        aliases = emptyList(),
        address = "Home address",
        latitude = 17.4,
        longitude = 78.5,
        isHome = true
    )

    @Test
    fun startupRequiresCompletedProfileAndSavedHome() {
        assertEquals(AppScreen.Onboarding, initialScreen(Profile(), emptyList()))
        assertEquals(AppScreen.Onboarding, initialScreen(Profile(), listOf(home)))
        assertEquals(AppScreen.Onboarding, initialScreen(Profile(completed = true), emptyList()))
        assertEquals(
            AppScreen.Onboarding,
            initialScreen(Profile(completed = true), listOf(home.copy(isHome = false)))
        )
        assertEquals(AppScreen.Home, initialScreen(Profile(completed = true), listOf(home)))
    }

    @Test
    fun animationOrderPreservesTheExistingFlow() {
        val ordered = listOf(
            AppScreen.Onboarding,
            AppScreen.Tutorial,
            AppScreen.Home,
            AppScreen.Settings,
            AppScreen.PlaceEditor,
            AppScreen.DestinationSearch,
            AppScreen.Clarification,
            AppScreen.RideConfirmation
        )
        ordered.zipWithNext()
            .forEach { (before, after) -> assertTrue(screenOrder(before) < screenOrder(after)) }
        assertEquals(screenOrder(AppScreen.Clarification), screenOrder(AppScreen.SharedChoices))
    }
}
