package com.ridesaathi.app.feature.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
        if (state.pickingAddress) {
            AddressPicker(state, word, onAction, Modifier.weight(1f), startEditing = true)
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (state.pendingHome) {
                    RideSectionTitle(word("onboardingHomeTitle"))
                } else {
                    RideSearchField(
                        value = state.draftName,
                        onValueChange = { onAction(PlaceEditorAction.Rename(it)) },
                        label = word("placeName"), icon = "pin", imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
                SectionCard {
                    Text(
                        word("address"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(state.draftAddress, style = MaterialTheme.typography.bodyLarge)
                    RideSecondaryButton(word("changeAddress"), onClick = {
                        focusManager.clearFocus()
                        onAction(PlaceEditorAction.ChangeAddress)
                    }, icon = "search")
                }
                mapUrl?.let { MapPreview(it, language) }
                if (state.editingId != null && !state.pendingHome) {
                    RideSecondaryButton(
                        label = word("delete"),
                        onClick = { onAction(PlaceEditorAction.AskDelete) },
                        contentColor = MaterialTheme.colorScheme.error
                    )
                }
            }
            RideActionFooter(word("save"), enabled = state.draftPosition != null && state.draftName.isNotBlank(), onClick = {
                focusManager.clearFocus()
                onAction(PlaceEditorAction.Save)
            })
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
