package com.ridesaathi.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ridesaathi.app.core.ui.components.RideLanguageChoices

/**
 * Settings language section. Shares [RideLanguageChoices] with the onboarding language step
 * so both screens present identical rows, badges and selection behaviour.
 */
@Composable
internal fun LanguagePicker(language: String, word: (String) -> String, onSelect: (String) -> Unit) {
    Text(
        word("language"),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
    RideLanguageChoices(language, onSelect)
}
