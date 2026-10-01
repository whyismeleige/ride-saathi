package com.ridesaathi.app.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.components.RideScreenHeader
import com.ridesaathi.app.core.ui.theme.RideTheme
import com.ridesaathi.app.domain.model.*
import com.ridesaathi.app.feature.destination.*
import com.ridesaathi.app.feature.home.*
import com.ridesaathi.app.feature.onboarding.*
import com.ridesaathi.app.feature.places.*
import com.ridesaathi.app.feature.ride.*
import com.ridesaathi.app.feature.settings.SettingsScreen
import com.ridesaathi.app.feature.sharedlocation.ClarificationScreen
import com.ridesaathi.app.feature.tutorial.TutorialController
import com.ridesaathi.app.localization.Words

private val previewHome =
    SavedPlace("preview-home", "Home", emptyList(), "Madhura Nagar, Ameerpet", 17.4, 78.4, true)
private val previewPlaces = listOf(
    previewHome,
    SavedPlace("work", "Work", emptyList(), "Begumpet, Hyderabad", 17.42, 78.42),
    SavedPlace("hospital", "KIMS Hospital", emptyList(), "Secunderabad", 17.43, 78.43),
    SavedPlace("temple", "Hanuman Temple", emptyList(), "Ameerpet", 17.44, 78.44)
)
private val word: (String) -> String = { Words.get("en", it) }

/** Fake fixtures above are preview-only; runtime screens are always driven by real state. */
@Composable
private fun PreviewShell(scroll: Boolean = false, home: Boolean = false, intro: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    RideTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                RideScreenHeader(onBack = if (home || intro) null else ({}), backLabel = word("back"), brandAtStart = home, trailing = { if (home) FilledTonalIconButton(onClick = {}, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = com.ridesaathi.app.core.ui.theme.RideColors.Mint)) { Text("P") } })
                Column(
                    Modifier.weight(1f)
                        .then(if (scroll) Modifier.verticalScroll(rememberScrollState()).padding(24.dp) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SetupPreview(step: OnboardingStep, name: String = "Piyush Jain") = PreviewShell(intro = step == OnboardingStep.Introduction) {
    OnboardingScreen(
        step, 0, List(3) { TutorialController.TutorialSlide("mic", "introAboutTitle", "introAboutBody") },
        Profile(name = if (step == OnboardingStep.Name) name else ""), emptyList(), true, word,
        {}, {}, {}, {}, {}, {}, {}, Modifier.fillMaxSize()
    )
}

@Preview(name = "Introduction", widthDp = 390, heightDp = 780)
@Composable internal fun IntroPreview() = SetupPreview(OnboardingStep.Introduction)

@Preview(name = "Language", widthDp = 390, heightDp = 780)
@Composable internal fun LanguagePreview() = SetupPreview(OnboardingStep.Language)

@Preview(name = "Name", widthDp = 390, heightDp = 780)
@Preview(name = "Name large text", widthDp = 320, heightDp = 640, fontScale = 1.6f)
@Composable internal fun NamePreview() = SetupPreview(OnboardingStep.Name)

@Preview(name = "Save home", widthDp = 390, heightDp = 780)
@Composable internal fun SaveHomePreview() = PreviewShell() {
    AddressPicker(PlaceEditorUiState().apply { pendingHome = true }, word, {}, Modifier.fillMaxSize())
}

@Preview(name = "Address results", widthDp = 390, heightDp = 780)
@Composable internal fun AddressPreview() = PreviewShell() {
    AddressPicker(
        PlaceEditorUiState().apply {
            pendingHome = true
            searchQuery = "Madhura Nagar, Ameerpet"
            searchResults = listOf(
                PlaceCandidate("Madhura Nagar, Ameerpet, Hyderabad", 17.4, 78.4),
                PlaceCandidate("Ameerpet Metro Station, Ameerpet Main Rd, Hyderabad", 17.41, 78.41),
                PlaceCandidate("S R Nagar, Sanjeeva Reddy Nagar, Hyderabad", 17.42, 78.42),
                PlaceCandidate("Begumpet, Near Begumpet, Hyderabad", 17.43, 78.43)
            )
        },
        word, {}, Modifier.fillMaxSize()
    )
}

@Preview(name = "Saved places and settings", widthDp = 390, heightDp = 780)
@Composable internal fun SettingsPreview() = PreviewShell() {
    SettingsScreen("en", previewPlaces, word, {}, {}, {}, {}, {}, Modifier.fillMaxSize())
}

@Preview(name = "Voice booking", widthDp = 390, heightDp = 780)
@Preview(name = "Booking large text", widthDp = 320, heightDp = 640, fontScale = 1.6f)
@Preview(name = "Booking landscape", widthDp = 800, heightDp = 360)
@Composable internal fun HomePreview() = PreviewShell(home = true) {
    HomeScreen(Profile(name = "Piyush"), previewPlaces, VoiceUiState(), word, {}, {}, greetingKey = "goodMorning")
}

@Preview(name = "Did you mean", widthDp = 390, heightDp = 780)
@Composable private fun ClarificationPreview() = PreviewShell(scroll = true) {
    ClarificationScreen(false, "", previewPlaces, "Sample destination", true, word, {}, {})
}

@Preview(name = "Destination search", widthDp = 390, heightDp = 780)
@Composable private fun DestinationSearchPreview() = PreviewShell(scroll = true) {
    DestinationSearchScreen(
        DestinationSearchState(
            query = "Sample Park",
            editing = true,
            candidates = listOf(
                PlaceCandidate("Sample Park, Sample City", 17.4, 78.4),
                PlaceCandidate("Sample Park Gate, Sample City", 17.41, 78.41),
                PlaceCandidate("S R Nagar, Sanjeeva Reddy Nagar, Hyderabad", 17.42, 78.42),
                PlaceCandidate("Begumpet, Near Begumpet, Hyderabad", 17.43, 78.43)
            )
        ),
        word, {}, {}
    )
}

@Preview(name = "Confirm Uber handoff", widthDp = 390, heightDp = 780)
@Composable private fun ConfirmationPreview() = PreviewShell(scroll = true) {
    RideConfirmationScreen(previewHome, false, true, false, "", "en", word, {}, {})
}

@Preview(name = "Name empty, keyboard space", widthDp = 360, heightDp = 424)
@Composable internal fun EmptyNamePreview() = SetupPreview(OnboardingStep.Name, name = "")
