package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
internal fun SearchLocationActions(
    hasPermission: Boolean,
    word: (String) -> String,
    onSettings: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    TextButton(onClick = onSettings) { Text(word(if (hasPermission) "openLocation" else "openAppSettings")) }
    if (onRetry != null) TextButton(onClick = onRetry) { Text(word("retry")) }
}
