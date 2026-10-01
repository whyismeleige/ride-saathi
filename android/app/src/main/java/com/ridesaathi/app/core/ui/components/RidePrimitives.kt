package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.ridesaathi.app.R
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.RideColors

/** Ride Saathi wordmark: car glyph plus name, used by the shell header and Settings. */
@Composable
internal fun RideSaathiLogo(modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(painterResource(R.drawable.brand_car), null, Modifier.size(28.dp))
        Text("Ride Saathi", modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = RideColors.Navy)
        Canvas(Modifier.size(20.dp).offset(x = (-6).dp, y = (-8).dp)) {
            val c = Color(0xFFFFA31A)
            drawCircle(c, size.width * .23f)
            repeat(8) { i ->
                val a = i * Math.PI / 4
                drawLine(c, center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (size.width * .34f), center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (size.width * .47f), size.width * .07f, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun CircularBackButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        Surface(shape = CircleShape, color = Color.White, shadowElevation = 2.dp, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { RideIcon("back", Modifier.size(24.dp), RideColors.Navy) }
        }
    }
}

@Composable
internal fun RideIconBadge(icon: String, home: Boolean = false, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val artwork = when { home || icon == "home" -> R.drawable.place_home; icon == "work" -> R.drawable.place_work; icon == "hospital" -> R.drawable.place_hospital; icon == "temple" -> R.drawable.place_temple; else -> null }
    if (artwork != null) {
        Image(painterResource(artwork), null, Modifier.size(size))
        return
    }
    Surface(shape = CircleShape, color = if (home) RideColors.Peach else if (icon == "result") Color(0xFFF0F2F5) else RideColors.Mint) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            RideIcon(icon, color = if (home) RideColors.Orange else if (icon == "result") RideColors.Slate else RideColors.Emerald)
        }
    }
}

/** Section heading with heading semantics so TalkBack can navigate between sections. */
@Composable
internal fun RideSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * The supported UI languages. Lives in `core/ui` because both the onboarding language step
 * and the Settings language section render it; neither feature owns the list.
 */
@Composable
internal fun RideLanguageChoices(language: String, onSelect: (String) -> Unit) {
    val options = listOf(
        LanguageOption("en", "English", "A", RideColors.Mint),
        LanguageOption("hi", "हिन्दी", "हिं", RideColors.Orange),
        LanguageOption("te", "తెలుగు", "తె", Color(0xFF9A80EF))
    )
    Column(
        Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { option ->
            RideSelectionCard(
                label = option.label,
                selected = language == option.code,
                subtitle = when(option.code) { "hi" -> "बोलकर बुक करें"; "te" -> "మాట్లాడి బుక్ చేయండి"; else -> "Speak naturally" },
                onSelect = { onSelect(option.code) }
            ) { RideLanguageBadge(option.glyph, option.badgeColor) }
        }
    }
}

private data class LanguageOption(
    val code: String,
    val label: String,
    val glyph: String,
    val badgeColor: Color
)
