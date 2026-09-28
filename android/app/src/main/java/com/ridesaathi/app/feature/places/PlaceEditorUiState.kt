package com.ridesaathi.app.feature.places

import androidx.compose.runtime.*
import com.ridesaathi.app.domain.model.PlaceCandidate

/** Observable state for the places feature; asynchronous handles stay in its controller. */
@Stable
internal class PlaceEditorUiState {
    var pendingHome by mutableStateOf(false)
    var editingId by mutableStateOf<String?>(null)
    var draftName by mutableStateOf("")
    var pickingAddress by mutableStateOf(false)
    var draftAddress by mutableStateOf("")
    var draftPosition by mutableStateOf<PlaceCandidate?>(null)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<PlaceCandidate>>(emptyList())
    var searching by mutableStateOf(false)
    var searchPending by mutableStateOf(false)
    var showDeleteConfirmation by mutableStateOf(false)
}
