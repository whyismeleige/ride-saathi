package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LargeButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = tween(180),
        label = "largeButtonPressScale"
    )
    Button(
        onClick = onClick, enabled = enabled, shape = com.ridesaathi.app.core.ui.theme.RidePill,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).scale(scale)
            .animateContentSize(tween(220, easing = FastOutSlowInEasing))
    ) {
        Text(
            label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis
        )
    }
}
