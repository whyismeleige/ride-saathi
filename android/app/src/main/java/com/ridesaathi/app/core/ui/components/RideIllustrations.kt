package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.ridesaathi.app.R

// Decorative-only crops, reproducible with docs/design/tools/extract_artwork.py.
// Every screen retains its own composition. Text and controls are always Compose.
@Composable private fun Artwork(id: Int, ratio: Float, modifier: Modifier) =
    Image(painterResource(id), null, modifier.fillMaxWidth().aspectRatio(ratio), contentScale = ContentScale.Crop)
@Composable internal fun IntroIllustration(modifier: Modifier = Modifier) = Artwork(R.drawable.intro_art, 922f / 635, modifier)
@Composable internal fun LanguageIllustration(modifier: Modifier = Modifier) = Artwork(R.drawable.language_art, 922f / 434, modifier)
@Composable internal fun NameIllustration(modifier: Modifier = Modifier) = Artwork(R.drawable.name_art, 924f / 395, modifier)
@Composable internal fun HomeIllustration(modifier: Modifier = Modifier) = Artwork(R.drawable.home_art, 883f / 452, modifier)
@Composable internal fun AddressIllustration(modifier: Modifier = Modifier) = Artwork(R.drawable.address_art, 883f / 337, modifier)
@Composable internal fun SavedPlacesIllustration(modifier: Modifier = Modifier) = Artwork(R.drawable.saved_places_art, 774f / 351, modifier)
@Composable internal fun BookingIllustration(modifier: Modifier = Modifier) = Artwork(R.drawable.booking_art, 915f / 601, modifier)
