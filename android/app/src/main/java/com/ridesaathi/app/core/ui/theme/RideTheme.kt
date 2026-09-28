package com.ridesaathi.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF23675C), onPrimary = Color.White,
            primaryContainer = Color(0xFFE0EFE8), onPrimaryContainer = Color(0xFF174D43),
            secondary = Color(0xFF586C64), secondaryContainer = Color(0xFFEAF0EA),
            background = Color(0xFFF7F8F2), onBackground = Color(0xFF253B34),
            surface = Color(0xFFFEFFFB), onSurface = Color(0xFF253B34),
            surfaceVariant = Color(0xFFEBEFE8), onSurfaceVariant = Color(0xFF58675F),
            outline = Color(0xFF78877D), outlineVariant = Color(0xFFDCE3DA),
            error = Color(0xFF9B3833), errorContainer = Color(0xFFFFEDE8)
        ),
        typography = Typography(
            headlineLarge = TextStyle(
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.SemiBold
            ),
            headlineMedium = TextStyle(
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.SemiBold
            ),
            titleLarge = TextStyle(
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold
            ),
            titleMedium = TextStyle(
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium
            ),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            labelLarge = TextStyle(
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
