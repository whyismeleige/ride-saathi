package com.ridesaathi.app.feature.home

import androidx.compose.runtime.*

/** Observable state for the home feature; asynchronous handles stay in its controller. */
@Stable
internal class VoiceUiState {
    var listening by mutableStateOf(false)
    var speechTranscript by mutableStateOf("")
    var transcriptIsFinal by mutableStateOf(false)
}
