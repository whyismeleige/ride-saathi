package com.ridesaathi.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.*

@Composable
internal fun LanguageChoices(language: String, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("en", "English", "A"), Triple("hi", "हिन्दी", "अ"), Triple("te", "తెలుగు", "అ")).forEachIndexed { index, (code, label, glyph) ->
            val selected = language == code
            Surface(shape = RideShapes.medium, color = if (selected) RideColors.Mint.copy(alpha = .45f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) RideColors.Emerald else RideColors.Border),
                modifier = Modifier.fillMaxWidth().clip(RideShapes.medium).selectable(selected, role = Role.RadioButton, onClick = { onSelect(code) })) {
                Row(Modifier.heightIn(min = 80.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = CircleShape, color = listOf(RideColors.Mint, RideColors.Peach, RideColors.Lavender)[index]) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text(glyph, style = MaterialTheme.typography.titleLarge) }
                    }
                    Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected, onClick = null)
                }
            }
        }
    }
}

@Composable
internal fun LanguagePicker(language: String, word: (String) -> String, onSelect: (String) -> Unit) {
    Text(word("language"), style = MaterialTheme.typography.titleLarge)
    LanguageChoices(language, onSelect)
}
