package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.RideColors
import com.ridesaathi.app.core.ui.theme.RideShapes
import com.ridesaathi.app.core.ui.theme.RideSpacing

/**
 * Secondary action paired with [LargeButton]: a pill outlined in the new palette rather than
 * a stock Material button. Keeps the 56dp minimum touch target used everywhere else.
 */
@Composable
internal fun RideSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null,
    contentColor: Color = RideColors.Emerald
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RideShapes.medium,
        border = BorderStroke(1.5.dp, RideColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        if (icon != null) {
            RideIcon(icon, Modifier.size(20.dp), color = LocalContentColor.current)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/**
 * Single-choice row used by the language step and the Settings language section.
 *
 * The whole row is selectable and the native radio indicator stays visible, so TalkBack
 * announces a radio button and sighted users still see the selection.
 */
@Composable
internal fun RideSelectionCard(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit
) {
    Surface(
        shape = RideShapes.medium,
        color = if (selected) RideColors.Mint.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) RideColors.Emerald else RideColors.Border
        ),
        modifier = modifier.fillMaxWidth().clip(RideShapes.medium)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            badge()
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            RadioButton(selected, onClick = null)
        }
    }
}

/** Circular script badge; a distinct tint per row keeps the three options distinguishable. */
@Composable
internal fun RideLanguageBadge(glyph: String, color: Color) {
    Surface(shape = CircleShape, color = color) {
        Box(Modifier.size(RideSpacing.Badge), contentAlignment = Alignment.Center) {
            Text(glyph, style = MaterialTheme.typography.titleLarge)
        }
    }
}
