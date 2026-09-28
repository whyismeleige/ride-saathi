package com.ridesaathi.app.feature.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.ExpandableAddress
import com.ridesaathi.app.core.ui.components.LargeButton
import com.ridesaathi.app.core.ui.components.MapPreview
import com.ridesaathi.app.core.ui.components.RideIcon
import com.ridesaathi.app.core.ui.components.SectionCard
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
    onLocationSettings: () -> Unit
) {
    Text(word("confirm"), style = MaterialTheme.typography.headlineMedium)
    SectionCard {
        RideIcon(if (place.isHome) "home" else "pin", Modifier.size(36.dp))
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
}

@Composable
internal fun AppSession.RideConfirmationRoute() {
    val place = ride.state.selected ?: return
    RideConfirmationScreen(
        place,
        ride.state.handoffInProgress,
        uberInstalled(),
        message == word("locationUnavailable"),
        OpenStreetMapPreviewProvider(mapEndpoint).url(place.latitude, place.longitude),
        profile.language,
        ::word,
        ::openStore,
        ::openLocationSettings
    )
}
