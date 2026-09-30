package com.ridesaathi.app.feature.places

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.core.ui.theme.RideShapes
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
    val inspection = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (inspection) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.searchQuery, onValueChange = {
                onAction(PlaceEditorAction.Query(it))
            },
            label = { Text(word("address")) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focusRequester),
            singleLine = true,
            leadingIcon = { RideIcon("search") },
            shape = RideShapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
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
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onAction(PlaceEditorAction.Search)
                        }, enabled = state.searchQuery.trim().length >= 3,
                        modifier = Modifier.padding(horizontal = 4.dp).size(48.dp)
                            .semantics { contentDescription = word("search") }) {
                        RideIcon("search", Modifier.size(24.dp), color = LocalContentColor.current)
                    }
                }
            })
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.searchResults, key = { it.address }) { result ->
                var expanded by remember(result.address) { mutableStateOf(false) }
                var overflows by remember(result.address) { mutableStateOf(false) }
                val title = result.address.substringBefore(',').trim().ifBlank { result.address }
                val remainder = result.address.removePrefix(title).trim().removePrefix(",").trim()
                OutlinedCard(onClick = {
                    focusManager.clearFocus()
                    onAction(PlaceEditorAction.SelectAddress(result))
                }, modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RideShapes.medium,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.padding(14.dp), verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RideIconBadge("pin")
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            if (remainder.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        remainder,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Clip,
                                        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
                                    )
                                    if (!expanded && overflows) {
                                        TextButton(
                                            onClick = { expanded = true },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                .semantics { contentDescription = word("showMore") }
                                        ) { Text("…", style = MaterialTheme.typography.titleMedium) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            word("searchAttribution"), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
        )
    }
}
