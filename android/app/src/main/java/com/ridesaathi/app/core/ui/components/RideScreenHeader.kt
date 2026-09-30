package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Screen-specific shell header.
 *
 * [onBack] is null on screens where Android Back is intentionally unavailable (the first
 * onboarding stage and Home), so the reference layout never shows a dead circular control.
 * [showBrand] is off for onboarding, where the stage heading already carries the identity.
 */
@Composable
internal fun RideScreenHeader(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "",
    showBrand: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) CircularBackButton(backLabel, onBack)
        if (showBrand) RideSaathiLogo(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}
