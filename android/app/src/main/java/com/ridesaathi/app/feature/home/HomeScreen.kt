package com.ridesaathi.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.ridesaathi.app.navigation.AppScreen

@Composable
internal fun HomeScreen(
    profile: Profile, places: List<SavedPlace>, voice: VoiceUiState,
    word: (String) -> String, onSelect: (SavedPlace) -> Unit, onMicrophone: () -> Unit,
    onSearch: () -> Unit = {}, onPlaces: () -> Unit = {}
) {
    Text("${word("hello")},\n${profile.name}!", style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() })
    Text(word("rideTo"), style = MaterialTheme.typography.bodyLarge, color = RideColors.Slate)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RideVoiceButton(voice.listening, word, onMicrophone)
        Text(word(if (voice.listening) "listening" else "tapSpeak"), style = MaterialTheme.typography.titleMedium)
        Text(word("voiceHint"), textAlign = TextAlign.Center, color = RideColors.Slate,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        SpeechTranscript(voice.speechTranscript, voice.transcriptIsFinal, word)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(word("quickPlaces"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onPlaces) { Text(word("seeAll")) }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(places.sortedByDescending { it.isHome }, key = { it.id }) { place ->
            Surface(onClick = { onSelect(place) }, shape = RideShapes.medium,
                color = if (place.isHome) RideColors.Mint.copy(alpha = .4f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, RideColors.Border), modifier = Modifier.width(148.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RideIconBadge(if (place.isHome) "home" else "pin", place.isHome)
                    Text(if (place.isHome) word("home") else place.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    RideCandidateCard(word("manualDestination"), "", onSearch)
    Text(word("handoffHint"), style = MaterialTheme.typography.bodyMedium, color = RideColors.Slate)
}

@Composable
internal fun AppSession.HomeRoute() {
    HomeScreen(profile, places, voice.state, ::word, { ride.choose(it) }, voice::toggleListening,
        { destination.beginDestinationSearch("", extractPhrase = false) },
        { shared.cancelSharedLocation(); voice.stopListening(); stopPrompt(); message = ""; screen = AppScreen.Settings })
}
