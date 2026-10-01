package com.ridesaathi.app.feature.destination

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.ui.components.*
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
    RideSectionTitle(word("searchDestinations"))
    if (state.editing) {
        RideSearchField(
            value = state.query,
            onValueChange = { onAction(DestinationSearchAction.EditQuery(it)) },
            label = word("searchQuery"),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide(); onAction(DestinationSearchAction.Search)
            })
        )
        LargeButton(word("search"), enabled = state.query.trim().length >= 3) {
            keyboard?.hide(); onAction(DestinationSearchAction.Search)
        }
    } else Text(state.query, style = MaterialTheme.typography.headlineMedium)
    AnimatedVisibility(state.loading, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
        RideLoadingState(word("searchingDestination"))
    }
    if (state.error == "searchLocationRequired") locationActions()
    state.error?.let { RideErrorState(word(it)) }
    if (!state.loading && !state.editing) {
        state.visible.forEachIndexed { index, candidate ->
            RideCandidateCard(
                title = "${index + 1}. ${candidate.address.substringBefore(',')}",
                address = candidate.address.substringAfter(',', "").trim(),
                onClick = { onAction(DestinationSearchAction.Select(index)) }
            )
        }
        if (state.page > 0) RideSecondaryButton(word("searchPrevious"), onClick = { onAction(DestinationSearchAction.Previous) })
        if (state.hasMore) RideSecondaryButton(word("searchMore"), onClick = { onAction(DestinationSearchAction.More) })
        RideSecondaryButton(word("searchAgain"), onClick = { onAction(DestinationSearchAction.Again) })
        if (state.retryable) LargeButton(word("retry")) { onAction(DestinationSearchAction.Search) }
    }
    Text(word("searchAttribution"), style = MaterialTheme.typography.bodySmall)
}

internal sealed interface DestinationSearchAction {
    data class EditQuery(val query: String) : DestinationSearchAction
    data class Select(val index: Int) : DestinationSearchAction
    data object Search : DestinationSearchAction
    data object More : DestinationSearchAction
    data object Previous : DestinationSearchAction
    data object Again : DestinationSearchAction
}

@Composable
internal fun AppSession.DestinationSearchScreen() {
    val state = destination.destinationSearch ?: return
    DestinationSearchScreen(state, ::word, destination::onAction) { SearchLocationActions() }
}
