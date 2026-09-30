package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.RideColors
import com.ridesaathi.app.core.ui.theme.RideSpacing

/** Ride Saathi wordmark: car glyph plus name, used by the shell header and Settings. */
@Composable
internal fun RideSaathiLogo(modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RideIcon("car", color = RideColors.Navy)
        Text("Ride Saathi", style = MaterialTheme.typography.titleMedium, color = RideColors.Navy)
    }
}

@Composable
internal fun CircularBackButton(label: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
    ) {
        RideIcon("back", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun RideIconBadge(icon: String, home: Boolean = false) {
    Surface(shape = CircleShape, color = if (home) RideColors.Peach else RideColors.Mint) {
        Box(Modifier.size(RideSpacing.Badge), contentAlignment = Alignment.Center) {
            RideIcon(icon, color = if (home) RideColors.Orange else RideColors.Emerald)
        }
    }
}

/** Section heading with heading semantics so TalkBack can navigate between sections. */
@Composable
internal fun RideSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * The supported UI languages. Lives in `core/ui` because both the onboarding language step
 * and the Settings language section render it; neither feature owns the list.
 */
@Composable
internal fun RideLanguageChoices(language: String, onSelect: (String) -> Unit) {
    val options = listOf(
        LanguageOption("en", "English", "A", RideColors.Mint),
        LanguageOption("hi", "हिन्दी", "अ", RideColors.Peach),
        LanguageOption("te", "తెలుగు", "అ", RideColors.Lavender)
    )
    Column(
        Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { option ->
            RideSelectionCard(
                label = option.label,
                selected = language == option.code,
                onSelect = { onSelect(option.code) }
            ) { RideLanguageBadge(option.glyph, option.badgeColor) }
        }
    }
}

private data class LanguageOption(
    val code: String,
    val label: String,
    val glyph: String,
    val badgeColor: Color
)
