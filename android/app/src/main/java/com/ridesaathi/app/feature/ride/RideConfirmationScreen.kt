package com.ridesaathi.app.feature.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    if (working) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    if (!uberInstalled) LargeButton(word("install"), onClick = onInstall)
    if (locationUnavailable) {
        OutlinedButton(
            onClick = onLocationSettings,
            modifier = Modifier.fillMaxWidth()
        ) { Text(word("openLocation")) }
    }
    MapPreview(mapUrl, language)
    Text(word("handoffHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    LargeButton(word(if (working) "working" else "yes"), enabled = !working && uberInstalled, onClick = onConfirm)
    OutlinedButton(onClick = onChooseAgain, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(word("no")) }
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
