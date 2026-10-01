package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

/**
 * The concentric mint rings behind the microphone.
 *
 * Kept separate from [RideVoiceButton] so the same pulse can be reused by the sticky
 * destination-screen microphone, and so the animation can be replaced wholesale later.
 * Idle is slow and shallow; listening is faster and wider so the state is obvious at a glance.
 */
@Composable
internal fun RideVoicePulse(listening: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "voicePulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.16f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (listening) 850 else 1900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceRing"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = RideColors.Mint.copy(alpha = .45f),
            modifier = Modifier.size(160.dp).scale(pulse)
        ) {}
        Surface(shape = CircleShape, color = RideColors.Mint, modifier = Modifier.size(132.dp)) {}
    }
}

/**
 * Dominant home-screen voice action: a large emerald microphone over pulsing mint rings.
 *
 * Exposes both an action label and a state description so TalkBack reports "Tap and speak"
 * versus "Stop listening, Listening…" rather than an unlabelled circle.
 */
@Composable
internal fun RideVoiceButton(listening: Boolean, word: (String) -> String, onToggle: () -> Unit, speaking: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) .94f else 1f,
        animationSpec = tween(180),
        label = "micPress"
    )
    Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) {
        RideVoicePulse(listening)
        Surface(
            onClick = onToggle,
            interactionSource = interaction,
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(104.dp)
                .scale(pressScale)
                .semantics {
                    role = Role.Button
                    contentDescription = word(if (speaking) "interruptSpeak" else if (listening) "stop" else "tapSpeak")
                    stateDescription = word(if (listening) "listening" else "tapSpeak")
                }
        ) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF25BC8A), Color(0xFF087661), Color(0xFF129675)))), contentAlignment = Alignment.Center) {
                RideIcon("mic", Modifier.size(46.dp), color = LocalContentColor.current)
            }
        }
    }
}
