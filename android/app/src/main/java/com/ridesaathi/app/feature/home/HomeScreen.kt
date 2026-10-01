package com.ridesaathi.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.core.ui.theme.*
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.navigation.openSettings

@Composable
internal fun HomeScreen(
    profile: Profile, places: List<SavedPlace>, voice: VoiceUiState,
    word: (String) -> String, onSelect: (SavedPlace) -> Unit,
    onMicrophone: () -> Unit, onSearch: () -> Unit = {}, onPlaces: () -> Unit = {},
    greetingKey: String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) { in 5..11 -> "goodMorning"; in 12..16 -> "goodAfternoon"; else -> "goodEvening" }
) {
    val sortedPlaces = remember(places) { places.sortedByDescending { it.isHome } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${word(greetingKey)}, ${profile.name}!", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp), modifier = Modifier.semantics { heading() })
                Text(word("rideTo"), style = MaterialTheme.typography.bodyLarge, color = RideColors.Slate)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(184.dp).clipToBounds(), contentAlignment = Alignment.Center) {
                    BookingIllustration(Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(RideColors.Cream, Color.Transparent))))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp).background(Brush.verticalGradient(listOf(Color.Transparent, RideColors.Cream))))
                    RideVoiceButton(voice.listening, word, onMicrophone, voice.speaking)
                }
                Text(word(if (voice.speaking) { if (voice.listening && voice.bargeInAvailable) "bargeInHint" else "tapInterruptHint" } else if (voice.listening) "listening" else "tapSpeak"), style = MaterialTheme.typography.titleLarge, color = RideColors.Navy)
                Text(word("voiceHint"), Modifier.padding(horizontal = 32.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp), color = RideColors.Slate, textAlign = TextAlign.Center)
                SpeechTranscript(voice.speechTranscript, voice.transcriptIsFinal, word)
            }
            Box(Modifier.padding(horizontal = 24.dp)) {
                RidePlaceCard(word("manualDestination"), "pin", onSearch)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(word("quickPlaces"), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onPlaces) { Text(word("seeAll")); RideIcon("arrow", Modifier.size(16.dp), RideColors.Slate) }
                }
                if (places.isEmpty()) Box(Modifier.padding(horizontal = 24.dp)) { RideEmptyState(word("choosePlace")) }
                else androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedPlaces.size, key = { sortedPlaces[it].id }) { index ->
                        val place = sortedPlaces[index]
                        Surface(onClick = { onSelect(place) }, shape = RideShapes.medium,
                            color = if (place.isHome) RideColors.Mint.copy(alpha = .25f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (place.isHome) RideColors.SelectedBorder else RideColors.Border), modifier = Modifier.width(164.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                RideIconBadge(placeIcon(place), place.isHome, 44.dp)
                                Text(if (place.isHome) word("home") else place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(place.address.substringBefore(','), style = MaterialTheme.typography.bodyMedium, color = RideColors.Slate, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
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
