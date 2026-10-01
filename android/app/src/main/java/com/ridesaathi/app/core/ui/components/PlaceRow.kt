package com.ridesaathi.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ridesaathi.app.domain.model.SavedPlace

/**
 * Saved-place row. Thin wrapper over [RidePlaceCard] so onboarding places, Settings and
 * Clarification all share one card treatment; only the address presentation differs.
 */
@Composable
internal fun PlaceRow(
    place: SavedPlace,
    word: (String) -> String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    RidePlaceCard(
        title = if (place.isHome) word("home") else place.name,
        badgeIcon = placeIcon(place),
        homeBadge = place.isHome,
        supporting = { ExpandableAddress(place, word) },
        onClick = onClick,
        modifier = modifier
    )
}

internal fun placeIcon(place: SavedPlace): String = when {
    place.isHome -> "home"
    place.name.contains("work", true) -> "work"
    place.name.contains("hospital", true) -> "hospital"
    place.name.contains("temple", true) -> "temple"
    else -> "pin"
}
