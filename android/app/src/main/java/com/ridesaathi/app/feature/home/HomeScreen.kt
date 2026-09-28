package com.ridesaathi.app.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.PlaceRow
import com.ridesaathi.app.core.ui.components.RideIcon
import com.ridesaathi.app.core.ui.components.SpeechTranscript
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace

@Composable
internal fun HomeScreen(
    profile: Profile,
    places: List<SavedPlace>,
    voice: VoiceUiState,
    word: (String) -> String,
    onSelect: (SavedPlace) -> Unit,
    onMicrophone: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        if (entered) 1f else 0f,
        tween(450),
        label = "homeContentAlpha"
    )
    val heroScale by animateFloatAsState(
        if (entered) 1f else 0.96f,
        tween(450),
        label = "homeHeroScale"
    )
    LaunchedEffect(Unit) { entered = true }
    Text(
        "${word("hello")}, ${profile.name}", style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(contentAlpha)
    )
    Text(
        word("rideTo"),
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.alpha(contentAlpha)
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().scale(heroScale).alpha(contentAlpha)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeMicrophone(voice.listening, word, onMicrophone)
            Text(
                if (voice.listening) word("listening") else word("voiceHint"),
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center
            )
            SpeechTranscript(voice.speechTranscript, voice.transcriptIsFinal, word)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.alpha(contentAlpha)
    ) {
        Text(word("places"), style = MaterialTheme.typography.titleLarge)
        Text(word("tapHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    places.sortedByDescending { it.isHome }.forEachIndexed { index, place ->
        val rowAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 350, delayMillis = 80 * index),
            label = "placeRowAlpha"
        )
        PlaceRow(place, word, Modifier.alpha(rowAlpha)) { onSelect(place) }
    }
    Text(
        word("handoffHint"), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(contentAlpha)
    )
}

@Composable
private fun HomeMicrophone(listening: Boolean, word: (String) -> String, onToggle: () -> Unit) {
    val label = if (listening) word("stop") else word("speak")
    val transition = rememberInfiniteTransition(label = "homeMicPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.32f else 1.08f,
        animationSpec = infiniteRepeatable(tween(if (listening) 950 else 1800), RepeatMode.Restart),
        label = "homeMicPulseScale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = if (listening) 0.28f else 0.12f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(if (listening) 950 else 1800), RepeatMode.Restart),
        label = "homeMicPulseAlpha"
    )
    val micScale by animateFloatAsState(
        targetValue = if (listening) 1.08f else 1f,
        animationSpec = tween(220),
        label = "homeMicScale"
    )
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
            modifier = Modifier.size(118.dp).scale(pulse)
        ) {}
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (listening) 0.72f else 0.42f),
            modifier = Modifier.size(126.dp)
        ) {}
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            shadowElevation = 10.dp,
            color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp).scale(micScale).semantics { contentDescription = label }
        ) {
            Box(contentAlignment = Alignment.Center) {
                RideIcon("mic", Modifier.size(42.dp), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
internal fun AppSession.HomeRoute() {
    HomeScreen(profile, places, voice.state, ::word, { ride.choose(it) }, voice::toggleListening)
}
