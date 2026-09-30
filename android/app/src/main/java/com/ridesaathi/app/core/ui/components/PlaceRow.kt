package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.domain.model.SavedPlace

@Composable
internal fun PlaceRow(
    place: SavedPlace,
    word: (String) -> String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 420f),
        label = "placeRowPressScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 0.dp else 2.dp,
        animationSpec = tween(180),
        label = "placeRowElevation"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().scale(scale),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = elevation
    ) {
        Row(
            Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RideIconBadge(if (place.isHome) "home" else "pin", place.isHome)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (place.isHome) word("home") else place.name,
                    style = MaterialTheme.typography.titleMedium
                )
                ExpandableAddress(place, word)
            }
            RideIcon("arrow", Modifier.size(18.dp))
        }
    }
}
