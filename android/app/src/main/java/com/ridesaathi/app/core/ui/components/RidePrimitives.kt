package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.*

@Composable
internal fun RideSaathiLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RideIcon("car", color = RideColors.Navy)
        Text("Ride Saathi", style = MaterialTheme.typography.titleMedium, color = RideColors.Navy)
    }
}

@Composable
internal fun CircularBackButton(label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface)) {
        RideIcon("back", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun RideIconBadge(icon: String, home: Boolean = false) {
    Surface(shape = CircleShape, color = if (home) RideColors.Peach else RideColors.Mint) {
        Box(Modifier.size(RideSpacing.Badge), contentAlignment = Alignment.Center) {
            RideIcon(icon, color = if (home) RideColors.Orange else RideColors.Emerald)
        }
    }
}

@Composable
internal fun RideSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

/** Entire result is a touch target; details wrap instead of being hidden behind a fixed height. */
@Composable
internal fun RideCandidateCard(title: String, address: String, onClick: () -> Unit, home: Boolean = false) {
    Surface(onClick = onClick, shape = RideShapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RideColors.Border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 80.dp).padding(RideSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RideIconBadge(if (home) "home" else "pin", home)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodyMedium, color = RideColors.Slate)
            }
            RideIcon("arrow", Modifier.size(18.dp), color = RideColors.Slate)
        }
    }
}
