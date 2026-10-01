package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ridesaathi.app.core.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent, disabledContentColor = RideColors.Slate),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp).scale(scale)
            .shadow(if (enabled) 10.dp else 0.dp, RidePill, ambientColor = RideColors.Emerald.copy(alpha = .18f), spotColor = RideColors.Emerald.copy(alpha = .18f))
            .background(Brush.verticalGradient(if (enabled) listOf(RideColors.GreenLight, RideColors.ButtonBottom) else listOf(Color(0xFFE6EBE8), Color(0xFFE6EBE8))), RidePill)
            .animateContentSize(tween(220, easing = FastOutSlowInEasing))
    ) {
        Text(
            label, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), textAlign = TextAlign.Center,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(14.dp))
        RideIcon("forward", Modifier.size(24.dp), LocalContentColor.current)
    }
}
