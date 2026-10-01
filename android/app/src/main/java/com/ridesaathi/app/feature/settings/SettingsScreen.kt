package com.ridesaathi.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ridesaathi.app.core.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.feature.tutorial.TutorialMode
import com.ridesaathi.app.navigation.AppScreen

@Composable
internal fun SettingsScreen(
    language: String,
    places: List<SavedPlace>,
    word: (String) -> String,
    onLanguageChange: (String) -> Unit,
    onEdit: (SavedPlace) -> Unit,
    onTutorial: () -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var preferences by remember { mutableStateOf(false) }
    val filtered = places.sortedByDescending { it.isHome }.filter {
        query.isBlank() || it.name.contains(query, true) || it.address.contains(query, true)
    }
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val roomy = maxHeight >= 640.dp
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (roomy) SavedPlacesIllustration(Modifier.height(112.dp).clip(RideShapes.large))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(word("places"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
                            TextButton(onClick = { preferences = true }) { Text(word("settings")) }
                        }
                        Text(word("savedPlacesHint").replace('\n', ' '), style = MaterialTheme.typography.bodyLarge, color = RideColors.Slate)
                        RideSearchField(query, { query = it }, word("searchSavedPlaces"), borderColor = RideColors.Slate.copy(alpha = .4f))
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (filtered.isEmpty()) item { RideEmptyState(word("noResults")) }
                items(filtered, key = { it.id }) { place -> PlaceRow(place, word) { onEdit(place) } }
                item { RidePlaceCard(word("addPlace"), "plus", onAdd, description = word("addPlaceHint")) }
            }
            RideActionFooter(word("done"), onDone)
        }
    }
    if (preferences) AlertDialog(onDismissRequest = { preferences = false },
        title = { Text(word("settings")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LanguagePicker(language, word, onLanguageChange)
            RideSecondaryButton(word("watchTutorial"), onClick = { preferences = false; onTutorial() }, icon = "mic")
        } }, confirmButton = { TextButton(onClick = { preferences = false }) { Text(word("done")) } })

}

@Composable
internal fun AppSession.SettingsRoute(modifier: Modifier = Modifier) {
    SettingsScreen(
        profile.language,
        places,
        ::word,
        ::selectLanguage,
        { editor.openEditor(it, it.isHome) },
        { tutorial.openTutorial(TutorialMode.Full, AppScreen.Settings) },
        { editor.openEditor(null, false) },
        { screen = AppScreen.Home },
        modifier
    )
}
