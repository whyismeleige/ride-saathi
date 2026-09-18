package com.ridesaathi.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
            headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}

/** Decorative line icons; the adjacent text supplies the accessible label. */
@Composable
fun RideIcon(kind: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(28.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x*s, y*s), Offset(x2*s, y2*s), strokeWidth = stroke.width, cap = StrokeCap.Round)
        when (kind) {
            "mic" -> {
                drawRoundRect(color, Offset(9*s, 2*s), Size(6*s, 13*s), androidx.compose.ui.geometry.CornerRadius(3*s), style = stroke)
                drawArc(color, 0f, 180f, false, Offset(5*s, 7*s), Size(14*s, 12*s), style = stroke)
                line(12f, 19f, 12f, 22f); line(8f, 22f, 16f, 22f)
            }
            "home" -> {
                val path = Path().apply { moveTo(3*s, 11*s); lineTo(12*s, 3*s); lineTo(21*s, 11*s); moveTo(5*s, 10*s); lineTo(5*s, 21*s); lineTo(19*s, 21*s); lineTo(19*s, 10*s) }
                drawPath(path, color, style = stroke)
                line(10f,21f,10f,14f); line(10f,14f,14f,14f); line(14f,14f,14f,21f)
            }
            "arrow" -> { line(8f,5f,15f,12f); line(15f,12f,8f,19f) }
            "back" -> { line(15f,5f,8f,12f); line(8f,12f,15f,19f) }
            "check" -> { line(5f,12f,10f,17f); line(10f,17f,20f,6f) }
            else -> {
                drawCircle(color, 7*s, Offset(12*s,9*s), style = stroke)
                drawCircle(color, 2*s, Offset(12*s,9*s), style = stroke)
                line(6f,13f,12f,22f); line(12f,22f,18f,13f)
            }
        }
    }
}
