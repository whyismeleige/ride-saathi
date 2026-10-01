package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.RideColors
import com.ridesaathi.app.core.ui.theme.RideSpacing

/**
 * Loading, error and empty treatments, so an in-flight search or an empty result list looks
 * like the rest of the app instead of a stock spinner on a blank surface. Every message is
 * supplied already localized, and is announced politely for TalkBack.
 */
@Composable
internal fun RideLoadingState(message: String, modifier: Modifier = Modifier) {
    MessageCard(
        message = message,
        icon = "search",
        container = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun RideErrorState(message: String, modifier: Modifier = Modifier) {
    MessageCard(
        message = message,
        icon = "close",
        container = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
    )
}

@Composable
internal fun RideEmptyState(message: String, modifier: Modifier = Modifier) {
    MessageCard(
        message = message,
        icon = "pin",
        container = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun MessageCard(
    message: String,
    icon: String,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    progress: @Composable (() -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = BorderStroke(1.dp, RideColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(RideSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(RideSpacing.Small),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RideIcon(icon, Modifier.size(22.dp), color = contentColor)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            progress?.invoke()
        }
    }
}
