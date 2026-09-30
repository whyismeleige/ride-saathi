package com.ridesaathi.app.feature.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.data.places.OpenStreetMapPreviewProvider
import com.ridesaathi.app.domain.model.SavedPlace

@Composable
internal fun RideConfirmationScreen(
    place: SavedPlace,
    working: Boolean,
    uberInstalled: Boolean,
    locationUnavailable: Boolean,
    mapUrl: String,
    language: String,
    word: (String) -> String,
    onInstall: () -> Unit,
    onLocationSettings: () -> Unit,
    onConfirm: () -> Unit = {},
    onChooseAgain: () -> Unit = {}
) {
    RideSectionTitle(word("confirm"))
    SectionCard {
        RideIconBadge(if (place.isHome) "home" else "pin", place.isHome)
        Text(
            if (place.isHome) word("home") else place.name,
            style = MaterialTheme.typography.headlineLarge
        )
        ExpandableAddress(place, word, style = MaterialTheme.typography.bodyLarge)
    }
    if (working) RideLoadingState(word("working"))
    if (!uberInstalled) {
        RideEmptyState(word("uberInstall"))
        LargeButton(word("install"), onClick = onInstall)
    }
    if (locationUnavailable) {
        RideSecondaryButton(
            label = word("openLocation"),
            onClick = onLocationSettings,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
    MapPreview(mapUrl, language)
    Text(word("handoffHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    LargeButton(word("yes"), enabled = !working && uberInstalled, onClick = onConfirm)
    RideSecondaryButton(word("no"), onClick = onChooseAgain, enabled = !working)
}

@Composable
internal fun AppSession.RideConfirmationRoute() {
    val place = ride.state.selected ?: return

    RideConfirmationScreen(
        place = place,
        working = ride.state.handoffInProgress,
        uberInstalled = uberInstalled(),
        locationUnavailable = message == word("locationUnavailable"),
        mapUrl = OpenStreetMapPreviewProvider(mapEndpoint).url(place.latitude, place.longitude),
        language = profile.language,
        word = ::word,
        onInstall = ::openStore,
        onLocationSettings = ::openLocationSettings,
        onConfirm = ride::confirmRide,
        onChooseAgain = destination::returnToChoices
    )
}
