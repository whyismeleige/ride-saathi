package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keeps the primary action within reach, including above the system keyboard. */
@Composable
internal fun RideActionFooter(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            LargeButton(label, enabled = enabled, onClick = onClick)
        }
    }
}
