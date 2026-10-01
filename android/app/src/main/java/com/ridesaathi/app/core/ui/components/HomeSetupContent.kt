package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.*

/** One clear entry into address search, with the required setup action always in reach. */
@Composable
internal fun HomeSetupContent(word: (String) -> String, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val compact = maxHeight < 480.dp
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (!compact) HomeIllustration(Modifier.clip(RideShapes.large))
                Text(word("onboardingHomeTitle"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Text(word("onboardingHomeHint").replace('\n', ' '), style = MaterialTheme.typography.bodyLarge, color = RideColors.Slate)
            }
            RideActionFooter(word("onboardingHomeSearch"), onSearch)
        }
    }
}
