package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.*

@Composable
internal fun RideSearchField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Search,
    keyboardActions: KeyboardActions = KeyboardActions(),
    focusRequester: FocusRequester? = null,
    supportingText: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    icon: String = "search",
    borderColor: androidx.compose.ui.graphics.Color = RideColors.SelectedBorder
) {
    Column(modifier.fillMaxWidth()) {
        Surface(shape = RideShapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = .7f),
            border = BorderStroke(1.dp, borderColor)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RideIcon(icon, Modifier.size(24.dp), if (icon == "person") RideColors.Emerald else RideColors.Navy)
                BasicTextField(value, onValueChange, enabled = enabled, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = RideColors.Navy),
                    cursorBrush = SolidColor(RideColors.Emerald),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction, capitalization = if (icon == "person") androidx.compose.ui.text.input.KeyboardCapitalization.Words else androidx.compose.ui.text.input.KeyboardCapitalization.None), keyboardActions = keyboardActions,
                    modifier = Modifier.weight(1f).semantics { contentDescription = label }
                        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                    decorationBox = { inner -> Box { if (value.isEmpty()) Text(label, color = RideColors.Slate, style = MaterialTheme.typography.bodyLarge); inner() } })
                trailing?.invoke()
            }
        }
        supportingText?.invoke()
    }
}
