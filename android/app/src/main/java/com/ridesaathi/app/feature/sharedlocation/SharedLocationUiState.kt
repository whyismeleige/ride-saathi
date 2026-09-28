package com.ridesaathi.app.feature.sharedlocation

import androidx.compose.runtime.*

/** Observable state for the sharedlocation feature; asynchronous handles stay in its controller. */
@Stable
internal class SharedLocationUiState {
    var resolvingSharedLocation by mutableStateOf(false)
    var sharedAddress by mutableStateOf("")
}
