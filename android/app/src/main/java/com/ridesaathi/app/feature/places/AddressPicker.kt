package com.ridesaathi.app.feature.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.components.RideIcon

@Composable
internal fun AddressPicker(
    state: PlaceEditorUiState,
    word: (String) -> String,
    onAction: (PlaceEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        OutlinedTextField(
            value = state.searchQuery, onValueChange = {
                onAction(PlaceEditorAction.Query(it))
            },
            label = { Text(word("address")) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focusManager.clearFocus()
                onAction(PlaceEditorAction.Search)
            }),
            supportingText = {
                Text(
                    word(
                        when {
                            state.searching -> "searchingAddress"
                            state.searchPending -> "searchPending"
                            state.searchResults.isNotEmpty() -> "selectAddress"
                            else -> "addressSearchHint"
                        }
                    ), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            },
            trailingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onAction(PlaceEditorAction.Search)
                        }, enabled = state.searchQuery.trim().length >= 3,
                        modifier = Modifier.semantics { contentDescription = word("search") }) {
                        RideIcon("search")
                    }
                }
            })
        Text(
            word("searchAttribution"), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.searchResults) { result ->
                OutlinedCard(onClick = {
                    focusManager.clearFocus()
                    onAction(PlaceEditorAction.SelectAddress(result))
                }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RideIcon("pin", Modifier.size(24.dp))
                        Text(
                            result.address,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
