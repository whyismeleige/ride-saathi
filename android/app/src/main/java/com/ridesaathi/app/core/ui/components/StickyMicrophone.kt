package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun StickyMicrophone(
    enabled: Boolean,
    listening: Boolean,
    word: (String) -> String,
    onToggle: () -> Unit,
    speaking: Boolean = false,
    bargeInAvailable: Boolean = false
) {
    val label = word(if (speaking) "interruptSpeak" else if (listening) "stop" else "speak")
    val micScale by animateFloatAsState(
        targetValue = if (listening) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
        label = "stickyMicScale"
    )
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggle,
                enabled = enabled,
                shape = CircleShape, contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(72.dp).scale(micScale)
                    .semantics { contentDescription = label; stateDescription = word(if (listening) "listening" else "tapSpeak") }) {
                RideIcon("mic", Modifier.size(32.dp), color = LocalContentColor.current)
            }
            AnimatedVisibility(listening, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
                Text(
                    word(if (speaking) { if (bargeInAvailable) "bargeInHint" else "tapInterruptHint" } else "listening"), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}
