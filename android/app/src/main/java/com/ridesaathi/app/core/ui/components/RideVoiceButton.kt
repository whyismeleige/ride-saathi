package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.RideColors

@Composable
internal fun RideVoiceButton(listening: Boolean, word: (String) -> String, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) .94f else 1f, tween(180), label = "micPress")
    val transition = rememberInfiniteTransition(label = "voicePulse")
    val pulse by transition.animateFloat(1f, if (listening) 1.16f else 1.04f,
        infiniteRepeatable(tween(if (listening) 850 else 1900), RepeatMode.Reverse), label = "voiceRing")
    Box(Modifier.size(216.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = RideColors.Mint.copy(alpha = .45f), modifier = Modifier.size(188.dp).scale(pulse)) {}
        Surface(shape = CircleShape, color = RideColors.Mint, modifier = Modifier.size(160.dp)) {}
        Surface(onClick = onToggle, interactionSource = interaction, shape = CircleShape,
            color = RideColors.Emerald, contentColor = MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface), shadowElevation = 6.dp,
            modifier = Modifier.size(132.dp).scale(pressScale).semantics {
                role = Role.Button
                contentDescription = word(if (listening) "stop" else "tapSpeak")
                stateDescription = word(if (listening) "listening" else "tapSpeak")
            }) {
            Box(contentAlignment = Alignment.Center) { RideIcon("mic", Modifier.size(52.dp), color = LocalContentColor.current) }
        }
    }
}
