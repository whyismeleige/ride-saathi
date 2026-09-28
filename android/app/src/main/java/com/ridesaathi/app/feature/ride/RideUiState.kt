package com.ridesaathi.app.feature.ride

import androidx.compose.runtime.*
import com.ridesaathi.app.domain.model.SavedPlace

/** Observable state for the ride feature; asynchronous handles stay in its controller. */
@Stable
internal class RideUiState {
    var selected by mutableStateOf<SavedPlace?>(null)
    var choices by mutableStateOf<List<SavedPlace>>(emptyList())
    var handoffInProgress by mutableStateOf(false)
}
