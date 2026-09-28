package com.ridesaathi.app.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.ridesaathi.app.domain.model.SavedPlace

@Composable
internal fun ExpandableAddress(
    place: SavedPlace,
    word: (String) -> String,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    var expanded by remember(place.id, place.address) { mutableStateOf(false) }
    var overflows by remember(place.id, place.address) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(260, easing = FastOutSlowInEasing))) {
        Text(
            place.address,
            modifier = Modifier.fillMaxWidth(),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) overflows = result.hasVisualOverflow
            }
        )
        if (expanded || overflows) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(word(if (expanded) "showLess" else "showMore"))
            }
        }
    }
}
