package com.ridesaathi.app.feature.places

import androidx.compose.foundation.layout.*
import com.ridesaathi.app.core.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.components.*
import com.ridesaathi.app.domain.model.PlaceCandidate

/**
 * The Home landing promotes the field to the top when the keyboard opens.
 * Search, selection and attribution still use the existing controller callbacks.
 */
@Composable
internal fun AddressPicker(
    state: PlaceEditorUiState,
    word: (String) -> String,
    onAction: (PlaceEditorAction) -> Unit,
    modifier: Modifier = Modifier,
    startEditing: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val inspection = LocalInspectionMode.current
    var editing by remember { mutableStateOf(startEditing || state.searchQuery.isNotBlank() || !state.pendingHome) }
    LaunchedEffect(editing) {
        if (editing && !inspection) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    if (!editing && state.pendingHome) {
        HomeSetupContent(word, { editing = true }, modifier)
        return
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            RideSearchField(
                value = state.searchQuery,
                onValueChange = { onAction(PlaceEditorAction.Query(it)) },
                label = word(if (state.pendingHome) "onboardingHomeSearch" else "address"),
                focusRequester = focusRequester,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                supportingText = {
                    if (state.searching || state.searchPending) Text(word("searchingAddress"), modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                },
                trailing = {
                    if (state.searching) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onAction(PlaceEditorAction.Query("")) }, modifier = Modifier.semantics { contentDescription = word("clear") }) {
                            RideIcon("close", Modifier.size(18.dp), RideColors.Slate)
                        }
                    }
                },
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onAction(PlaceEditorAction.Search)
                })
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.searchResults.isEmpty() && !state.searching && !state.searchPending) {
                    item { RideEmptyState(word(if (state.searchQuery.length >= 3) "noResults" else "addressSearchHint")) }
                }
                items(state.searchResults, key = { it.address }) { result ->
                    AddressResultRow(result, word, result == state.searchResults.first()) {
                        focusManager.clearFocus()
                        onAction(PlaceEditorAction.SelectAddress(result))
                    }
                }
            }
            if (state.searchResults.isNotEmpty()) {
                RideActionFooter(word("continue"), enabled = !state.searching && !state.searchPending, onClick = {
                    focusManager.clearFocus()
                    state.searchResults.firstOrNull()?.let { onAction(PlaceEditorAction.SelectAddress(it)) }
                })
            }
            Text(word("searchAttribution"), style = MaterialTheme.typography.labelSmall, color = RideColors.Slate,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
        }
    }
}

/** Address split into a title line and the remaining locality, expandable when it overflows. */
@Composable
private fun AddressResultRow(
    result: PlaceCandidate,
    word: (String) -> String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    var expanded by remember(result.address) { mutableStateOf(false) }
    var overflows by remember(result.address) { mutableStateOf(false) }
    val title = result.address.substringBefore(',').trim().ifBlank { result.address }
    val remainder = result.address.removePrefix(title).trim().removePrefix(",").trim()
    RidePlaceCard(
        title = title,
        badgeIcon = if (highlighted) "pin" else "result",
        highlighted = highlighted,
        onClick = onClick,
        supporting = {
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
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = word("showMore") }
                    ) { Text("…", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        })
}
