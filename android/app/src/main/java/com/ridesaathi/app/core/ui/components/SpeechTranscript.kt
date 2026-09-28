package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SpeechTranscript(transcript: String, isFinal: Boolean, word: (String) -> String) {
    if (transcript.isBlank()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                word(if (isFinal) "recognizedSpeech" else "heardSoFar"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(transcript, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
