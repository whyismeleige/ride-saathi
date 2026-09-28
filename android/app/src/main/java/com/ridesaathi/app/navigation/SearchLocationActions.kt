package com.ridesaathi.app.navigation

import androidx.compose.runtime.Composable
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.SearchLocationActions

@Composable
internal fun AppSession.SearchLocationActions() {
    SearchLocationActions(
        hasSearchLocationPermission(), ::word,
        onSettings = { if (hasSearchLocationPermission()) openLocationSettings() else openAppSettings() },
        onRetry = if (screen == AppScreen.DestinationSearch) null else ({
            if (screen == AppScreen.PlaceEditor) editor.searchAddress() else shared.handleSharedLocation(
                intent
            )
        })
    )
}
