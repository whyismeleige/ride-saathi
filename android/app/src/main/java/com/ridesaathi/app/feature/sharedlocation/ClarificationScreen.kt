package com.ridesaathi.app.feature.sharedlocation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
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
    RideScenicHeader()
    RideSectionTitle(word(if (shared) "selectSharedLocation" else "ambiguous"))
    if (shared) {
        Text(word("sharedSearchHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Informational only: the link's address is shown for checking, not selectable.
        SectionCard {
            RideIconBadge("pin")
            Text(address, style = MaterialTheme.typography.titleMedium)
        }
    } else SpeechTranscript(transcript, transcriptIsFinal, word)
    choices.forEach { RideCandidateCard(if (it.isHome) word("home") else it.name, it.address, { onSelect(it) }, it.isHome) }
    RideSecondaryButton(word("cancel"), onClick = onCancel)
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
