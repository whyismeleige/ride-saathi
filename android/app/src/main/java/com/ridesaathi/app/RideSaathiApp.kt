package com.ridesaathi.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ridesaathi.app.core.ui.theme.RideTheme
import com.ridesaathi.app.navigation.AppNavigation

@Composable
internal fun RideSaathiApp(session: AppSession) {
    RideTheme { Surface(Modifier.fillMaxSize()) { session.AppNavigation() } }
}
