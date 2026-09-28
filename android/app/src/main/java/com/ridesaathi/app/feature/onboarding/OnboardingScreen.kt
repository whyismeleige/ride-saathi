package com.ridesaathi.app.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.LargeButton
import com.ridesaathi.app.core.ui.components.PlaceRow
import com.ridesaathi.app.core.ui.components.RideIcon
import com.ridesaathi.app.core.ui.components.SectionCard
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import com.ridesaathi.app.feature.settings.LanguagePicker
import com.ridesaathi.app.feature.tutorial.TutorialMode
import com.ridesaathi.app.navigation.AppScreen

@Composable
internal fun OnboardingScreen(
    profile: Profile,
    places: List<SavedPlace>,
    uberInstalled: Boolean,
    word: (String) -> String,
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onIntro: () -> Unit,
    onEdit: (SavedPlace) -> Unit
) {
    Text(word("welcome"), style = MaterialTheme.typography.headlineMedium)
    Text(word("setupHint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    SectionCard { LanguagePicker(profile.language, word, onLanguageChange, dropdown = true) }
    if (!profile.introSeen) {
        OutlinedButton(
            onClick = onIntro,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            RideIcon("arrow", Modifier.size(18.dp))
            Text(word("watchIntro"))
        }
    }
    OutlinedTextField(
        value = profile.name,
        onValueChange = onNameChange,
        label = { Text(word("name")) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Text(word("places"), style = MaterialTheme.typography.titleLarge)
    places.forEach { place -> PlaceRow(place, word) { onEdit(place) } }
    if (!uberInstalled) {
        Text(word("uberInstall"), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun OnboardingActions(
    hasHome: Boolean,
    uberInstalled: Boolean,
    word: (String) -> String,
    onAddHome: () -> Unit,
    onFinish: () -> Unit,
    onAddPlace: () -> Unit,
    onInstall: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasHome) {
            LargeButton(word("addHome"), onClick = onAddHome)
        } else {
            LargeButton(word("finish"), onClick = onFinish)
            OutlinedButton(
                onClick = onAddPlace,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(word("addPlace")) }
            if (!uberInstalled) LargeButton(word("install"), onClick = onInstall)
        }
    }
}

@Composable
internal fun AppSession.OnboardingRoute() {
    OnboardingScreen(
        profile,
        places,
        uberInstalled(),
        ::word,
        onboarding::updateName,
        ::selectLanguage,
        { tutorial.openTutorial(TutorialMode.Intro, AppScreen.Onboarding) },
        { editor.openEditor(it, it.isHome) })
}

@Composable
internal fun AppSession.OnboardingActionsRoute() {
    OnboardingActions(
        places.any { it.isHome },
        uberInstalled(),
        ::word,
        { editor.openEditor(null, true) },
        onboarding::finish,
        { editor.openEditor(null, false) },
        ::openStore
    )
}
