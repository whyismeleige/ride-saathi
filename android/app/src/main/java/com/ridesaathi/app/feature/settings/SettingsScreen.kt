package com.ridesaathi.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.core.ui.components.LargeButton
import com.ridesaathi.app.core.ui.components.PlaceRow
import com.ridesaathi.app.core.ui.components.RideIcon
import com.ridesaathi.app.core.ui.components.SectionCard
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
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            RideScenicHeader(home = true)
            RideSectionTitle(word("places"))
            Text(word("savedPlacesHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            places.sortedByDescending { it.isHome }.forEach { PlaceRow(it, word) { onEdit(it) } }
            LargeButton(word("addPlace"), onClick = onAdd)
            SectionCard { LanguagePicker(language, word, onLanguageChange) }
            OutlinedButton(onClick = onTutorial, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(word("watchTutorial"))
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            LargeButton(word("done"), onClick = onDone)
        }
    }
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
