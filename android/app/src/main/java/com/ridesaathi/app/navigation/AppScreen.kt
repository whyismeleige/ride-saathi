package com.ridesaathi.app.navigation

import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace

enum class AppScreen { Onboarding, Tutorial, Home, Settings, PlaceEditor, DestinationSearch, Clarification, SharedChoices, RideConfirmation }

/** Setup requires both a completed profile and an existing Home, including legacy data. */
internal fun initialScreen(profile: Profile, places: List<SavedPlace>): AppScreen =
    if (profile.completed && places.any { it.isHome }) AppScreen.Home else AppScreen.Onboarding

internal fun screenOrder(value: AppScreen) = when (value) {
    AppScreen.Onboarding -> 0
    AppScreen.Tutorial -> 1
    AppScreen.Home -> 2
    AppScreen.Settings -> 3
    AppScreen.PlaceEditor -> 4
    AppScreen.DestinationSearch -> 5
    AppScreen.Clarification, AppScreen.SharedChoices -> 6
    AppScreen.RideConfirmation -> 7
}
