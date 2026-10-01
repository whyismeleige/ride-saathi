package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Reserved action slots keep the wordmark clear of Back and profile at larger font sizes. */
@Composable
internal fun RideScreenHeader(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    showBrand: Boolean = true,
    brandAtStart: Boolean = false,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!brandAtStart) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onBack != null) CircularBackButton(backLabel, onBack)
        }
        Box(Modifier.weight(1f), contentAlignment = if (brandAtStart) Alignment.CenterStart else Alignment.Center) {
            if (showBrand) RideSaathiLogo()
        }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
            if (trailing != null) Row(content = trailing)
        }
    }
}
