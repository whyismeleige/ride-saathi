package com.ridesaathi.app.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.core.ui.theme.RideTheme
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.localization.Words

private val word: (String) -> String = { Words.get("en", it) }

private val samplePlace =
    SavedPlace("sample", "Sample clinic", emptyList(), "12 Sample Road, Sample City", 17.4, 78.4)

@Composable
private fun ComponentShell(content: @Composable ColumnScope.() -> Unit) {
    RideTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Preview(name = "Components", widthDp = 390, heightDp = 844)
@Composable
private fun ComponentsPreview() = ComponentShell {
    RideScreenHeader(onBack = {}, backLabel = word("back"))
    RideScreenHeader(
        onBack = {},
        backLabel = word("back"),
        trailing = { RideHeaderAction(word("settings"), "person") {} }
    )
    LargeButton(word("continue"), onClick = {})
    RideSecondaryButton(word("searchAgain"), onClick = {}, icon = "search")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LargeButton(word("back"), onClick = {}, modifier = Modifier.weight(1f))
        LargeButton(word("yes"), onClick = {}, modifier = Modifier.weight(1f))
    }
    RideLanguageChoices("hi") {}
    PlaceRow(samplePlace, word) {}
    RideCandidateCard(word("home"), "1 Sample Road, Sample City", {}, home = true)
    RideLoadingState(word("searchingDestination"))
    RideErrorState(word("locationUnavailable"))
    RideEmptyState(word("noResults"))
    SpeechTranscript(word("heardSoFar"), false, word)
}

@Preview(name = "Voice idle and listening", widthDp = 420, heightDp = 300)
@Composable
private fun VoicePreview() = ComponentShell {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RideVoiceButton(listening = false, word = word, onToggle = {})
        RideVoiceButton(listening = true, word = word, onToggle = {})
    }
}

@Preview(name = "Scenic header", widthDp = 390, heightDp = 240)
@Composable
private fun ScenicPreview() = ComponentShell {
    RideScenicHeader(home = true)
    RideScenicHeader()
}
