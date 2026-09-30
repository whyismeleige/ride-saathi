package com.ridesaathi.app.core.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun RideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RideColors.Emerald, onPrimary = Color.White,
            primaryContainer = RideColors.Mint, onPrimaryContainer = RideColors.Emerald,
            secondary = RideColors.Slate, onSecondary = Color.White,
            secondaryContainer = RideColors.Sky, onSecondaryContainer = RideColors.Navy,
            tertiary = RideColors.Orange, tertiaryContainer = RideColors.Peach,
            background = RideColors.Cream, onBackground = RideColors.Navy,
            surface = Color.White, onSurface = RideColors.Navy,
            surfaceVariant = RideColors.Mint, onSurfaceVariant = RideColors.Slate,
            surfaceContainerLow = Color.White, surfaceContainer = RideColors.Cream,
            surfaceContainerHigh = RideColors.Mint,
            outline = RideColors.Slate, outlineVariant = RideColors.Border,
            error = RideColors.Red, onError = Color.White,
            errorContainer = Color(0xFFFFEBEB), onErrorContainer = RideColors.Red
        ),
        typography = RideTypography,
        shapes = RideShapes,
        content = content
    )
}
