package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.RideColors

/**
 * The one card treatment for saved places, address results and destination candidates.
 *
 * Rows are height-unbounded so long addresses wrap instead of being clipped, and the whole
 * card is a single touch target. [supporting] lets each caller supply its own second line
 * (an expandable saved address, a plain locality, or nothing at all).
 *
 * [highlighted] only tints the surface mint; it is visual emphasis, not a selection state,
 * because no current flow keeps a persistent selection on these cards.
 */
@Composable
internal fun RidePlaceCard(
    title: String,
    badgeIcon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    homeBadge: Boolean = false,
    highlighted: Boolean = false,
    description: String? = null,
    supporting: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 420f),
        label = "placeCardPressScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 0.dp else 2.dp,
        animationSpec = tween(180),
        label = "placeCardElevation"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) RideColors.Mint.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .animateContentSize(tween(220, easing = FastOutSlowInEasing))
    ) {
        Row(
            Modifier.heightIn(min = 80.dp).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RideIconBadge(badgeIcon, homeBadge)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                supporting?.invoke()
            }
            trailing?.invoke() ?: RideIcon("arrow", Modifier.size(18.dp))
        }
    }
}

/** Search-result and destination-candidate card whose second line is a plain locality. */
@Composable
internal fun RideCandidateCard(
    title: String,
    address: String,
    onClick: () -> Unit,
    home: Boolean = false
) {
    RidePlaceCard(
        title = title,
        badgeIcon = if (home) "home" else "pin",
        homeBadge = home,
        description = address,
        onClick = onClick
    )
}

/** Header action sharing the circular container used by the back control. */
@Composable
internal fun RideHeaderAction(label: String, icon: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
    ) {
        RideIcon(icon, color = MaterialTheme.colorScheme.onSurface)
    }
}
