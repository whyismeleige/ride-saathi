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
        badgeIcon = if (place.isHome) "home" else "pin",
        homeBadge = place.isHome,
        supporting = { ExpandableAddress(place, word) },
        onClick = onClick,
        modifier = modifier
    )
}
