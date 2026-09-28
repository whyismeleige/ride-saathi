package com.ridesaathi.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.components.RideIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguagePicker(
    language: String,
    word: (String) -> String,
    onSelect: (String) -> Unit,
    dropdown: Boolean = false
) {
    val languages = listOf("en" to "English", "hi" to "हिन्दी", "te" to "తెలుగు")
    Text(word("language"), style = MaterialTheme.typography.titleLarge)
    if (dropdown) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = languages.firstOrNull { it.first == language }?.second ?: "English",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(word("changeLanguage")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                languages.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { onSelect(code); expanded = false },
                        trailingIcon = {
                            if (language == code) RideIcon(
                                "check",
                                Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.heightIn(min = 52.dp)
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            languages.forEach { (code, label) ->
                FilterChip(
                    selected = language == code, onClick = { onSelect(code) },
                    label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    leadingIcon = { if (language == code) RideIcon("check", Modifier.size(20.dp)) })
            }
        }
    }
}
