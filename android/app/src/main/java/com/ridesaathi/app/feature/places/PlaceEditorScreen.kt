package com.ridesaathi.app.feature.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.LargeButton
import com.ridesaathi.app.core.ui.components.MapPreview
import com.ridesaathi.app.core.ui.components.SectionCard
import com.ridesaathi.app.data.places.OpenStreetMapPreviewProvider

@Composable
internal fun PlaceEditorScreen(
    state: PlaceEditorUiState,
    language: String,
    mapUrl: String?,
    word: (String) -> String,
    onAction: (PlaceEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Column(modifier.fillMaxWidth()) {
        if (state.pickingAddress) {
            AddressPicker(state, word, onAction, Modifier.weight(1f))
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.pendingHome) {
                    Text(word("home"), style = MaterialTheme.typography.titleLarge)
                } else {
                    OutlinedTextField(
                        value = state.draftName,
                        onValueChange = { onAction(PlaceEditorAction.Rename(it)) },
                        label = { Text(word("placeName")) },
                        supportingText = { Text(word("placeNameHint")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
                SectionCard {
                    Text(
                        word("address"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(state.draftAddress, style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = {
                        focusManager.clearFocus()
                        onAction(PlaceEditorAction.ChangeAddress)
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(word("changeAddress"))
                    }
                }
                mapUrl?.let { MapPreview(it, language) }
                if (state.editingId != null && !state.pendingHome) {
                    TextButton(
                        onClick = { onAction(PlaceEditorAction.AskDelete) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(word("delete"))
                    }
                }
            }
            Surface(shadowElevation = 4.dp) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    LargeButton(
                        word("save"),
                        enabled = state.draftPosition != null && state.draftName.isNotBlank()
                    ) {
                        focusManager.clearFocus()
                        onAction(PlaceEditorAction.Save)
                    }
                }
            }
        }
    }
    if (state.showDeleteConfirmation) AlertDialog(
        onDismissRequest = { onAction(PlaceEditorAction.DismissDelete) },
        title = { Text(word("delete")) }, text = { Text(state.draftName) },
        confirmButton = { TextButton(onClick = { onAction(PlaceEditorAction.Delete) }) { Text(word("delete")) } },
        dismissButton = {
            TextButton(onClick = { onAction(PlaceEditorAction.DismissDelete) }) {
                Text(
                    word("cancel")
                )
            }
        })
}

@Composable
internal fun AppSession.PlaceEditorRoute(modifier: Modifier = Modifier) {
    val mapUrl = editor.state.draftPosition?.let {
        OpenStreetMapPreviewProvider(mapEndpoint).url(
            it.latitude,
            it.longitude
        )
    }
    PlaceEditorScreen(editor.state, profile.language, mapUrl, ::word, editor::onAction, modifier)
}
