package com.ridesaathi.app.feature.sharedlocation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.PlaceRow
import com.ridesaathi.app.core.ui.components.SpeechTranscript
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.navigation.AppScreen

@Composable
internal fun ClarificationScreen(
    shared: Boolean,
    address: String,
    choices: List<SavedPlace>,
    transcript: String,
    transcriptIsFinal: Boolean,
    word: (String) -> String,
    onSelect: (SavedPlace) -> Unit,
    onCancel: () -> Unit
) {
    Text(
        word(if (shared) "selectSharedLocation" else "ambiguous"),
        style = MaterialTheme.typography.headlineMedium
    )
    if (shared) {
        Text(word("sharedSearchHint"))
        Text(address, style = MaterialTheme.typography.bodyLarge)
    } else SpeechTranscript(transcript, transcriptIsFinal, word)
    choices.forEach { PlaceRow(it, word) { onSelect(it) } }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(word("cancel")) }
}

@Composable
internal fun AppSession.ClarificationRoute() {
    ClarificationScreen(
        screen == AppScreen.SharedChoices,
        shared.state.sharedAddress,
        ride.state.choices,
        voice.state.speechTranscript,
        voice.state.transcriptIsFinal,
        ::word,
        { ride.choose(it) },
        ride::cancelRide
    )
}
