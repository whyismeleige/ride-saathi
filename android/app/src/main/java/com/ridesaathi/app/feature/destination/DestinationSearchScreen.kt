package com.ridesaathi.app.feature.destination

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.LargeButton
import com.ridesaathi.app.domain.search.SpeechText
import com.ridesaathi.app.navigation.SearchLocationActions

@Composable
internal fun DestinationSearchScreen(
    state: DestinationSearchState,
    word: (String) -> String,
    onAction: (DestinationSearchAction) -> Unit,
    locationActions: @Composable () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Text(word("searchDestinations"), style = MaterialTheme.typography.headlineMedium)
    if (state.editing) {
        OutlinedTextField(
            value = state.query, onValueChange = {
            onAction(DestinationSearchAction.EditQuery(it))
        }, label = { Text(word("searchQuery")) }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide(); onAction(DestinationSearchAction.Search)
            }), singleLine = true
        )
        LargeButton(word("search"), enabled = state.query.trim().length >= 3) {
            keyboard?.hide(); onAction(DestinationSearchAction.Search)
        }
    } else Text(state.query, style = MaterialTheme.typography.titleLarge)
    AnimatedVisibility(state.loading, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word("searchingDestination"))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (state.error == "searchLocationRequired") locationActions()
    state.error?.let {
        Text(
            word(it),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (!state.loading && !state.editing) {
        state.visible.forEachIndexed { index, candidate ->
            LargeButton(
                "${index + 1}. ${SpeechText.addressSummary(candidate.address)}",
                maxLines = 2
            ) {
                onAction(DestinationSearchAction.Select(index))
            }
        }
        if (state.retryable) LargeButton(word("retry")) { onAction(DestinationSearchAction.Search) }
    }
    Text(word("searchAttribution"), style = MaterialTheme.typography.bodySmall)
}

internal sealed interface DestinationSearchAction {
    data class EditQuery(val query: String) : DestinationSearchAction
    data class Select(val index: Int) : DestinationSearchAction
    data object Search : DestinationSearchAction
}

@Composable
internal fun AppSession.DestinationSearchScreen() {
    val state = destination.destinationSearch ?: return
    DestinationSearchScreen(state, ::word, destination::onAction) { SearchLocationActions() }
}
