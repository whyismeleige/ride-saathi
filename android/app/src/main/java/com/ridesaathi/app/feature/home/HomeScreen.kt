package com.ridesaathi.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.core.ui.theme.*
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.navigation.openSettings

/**
 * The booking screen. Voice is the dominant action: the microphone sits in the middle of the
 * screen over concentric mint rings, with quick places and manual entry beneath it as quieter
 * alternatives for anyone who prefers to tap.
 */
@Composable
internal fun HomeScreen(
    profile: Profile,
    places: List<SavedPlace>,
    voice: VoiceUiState,
    word: (String) -> String,
    onSelect: (SavedPlace) -> Unit,
    onMicrophone: () -> Unit,
    onSearch: () -> Unit = {},
    onPlaces: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${word("hello")},\n${profile.name}!",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                word("rideTo"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RideVoiceButton(voice.listening, word, onMicrophone)
            Text(
                word(if (voice.listening) "listening" else "tapSpeak"),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                word("voiceHint"),
                textAlign = TextAlign.Center,
                color = RideColors.Slate,
                modifier = Modifier.padding(top = 6.dp)
            )
            SpeechTranscript(voice.speechTranscript, voice.transcriptIsFinal, word)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                word("quickPlaces"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            TextButton(onClick = onPlaces) { Text(word("seeAll")) }
        }
        if (places.isEmpty()) {
            RideEmptyState(word("choosePlace"))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(places.sortedByDescending { it.isHome }, key = { it.id }) { place ->
                    QuickPlaceCard(place, word) { onSelect(place) }
                }
            }
        }
        RidePlaceCard(
            title = word("manualDestination"),
            badgeIcon = "pin",
            onClick = onSearch
        )
        Text(word("handoffHint"), style = MaterialTheme.typography.bodyMedium, color = RideColors.Slate)
    }
}

/** Home always leads the row; every other saved place keeps its own user-given name. */
@Composable
private fun QuickPlaceCard(place: SavedPlace, word: (String) -> String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RideShapes.medium,
        color = if (place.isHome) RideColors.Mint.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RideColors.Border),
        modifier = Modifier.width(150.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RideIconBadge(if (place.isHome) "home" else "pin", place.isHome)
            Text(
                if (place.isHome) word("home") else place.name,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
        }
    }
}

@Composable
internal fun AppSession.HomeRoute() {
    HomeScreen(
        profile,
        places,
        voice.state,
        ::word,
        { ride.choose(it) },
        voice::toggleListening,
        { destination.beginDestinationSearch("", extractPhrase = false) },
        ::openSettings
    )
}
