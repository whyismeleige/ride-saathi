package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.ridesaathi.app.R

/** Decorative icons; the adjacent text supplies the accessible label. */
@Composable
fun RideIcon(
    kind: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (kind == "uber") {
        Image(
            painter = painterResource(R.drawable.ic_uber),
            contentDescription = null,
            modifier = modifier.size(28.dp)
        )
        return
    }
    Canvas(modifier.size(28.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(
                color,
                Offset(x * s, y * s),
                Offset(x2 * s, y2 * s),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        when (kind) {
            "car" -> {
                drawRoundRect(color, Offset(2*s,10*s),Size(20*s,9*s), androidx.compose.ui.geometry.CornerRadius(3*s))
                val roof = Path().apply { moveTo(5*s,10*s); lineTo(8*s,4*s); lineTo(17*s,4*s); lineTo(20*s,10*s); close() }
                drawPath(roof,color,style=stroke)
                drawCircle(color,2*s,Offset(6*s,21*s)); drawCircle(color,2*s,Offset(18*s,21*s))
            }
            "plus" -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }

            "person" -> {
                drawCircle(color, 4 * s, Offset(12 * s, 7 * s))
                drawArc(color, 180f, 180f, true, Offset(4 * s, 13 * s), Size(16 * s, 14 * s))
            }

            "language" -> {
                drawCircle(color, 9 * s, Offset(12 * s, 12 * s), style = stroke)
                drawOval(color, Offset(8 * s, 3 * s), Size(8 * s, 18 * s), style = stroke)
                line(3f, 12f, 21f, 12f)
            }

            "mic" -> {
                drawRoundRect(
                    color,
                    Offset(9 * s, 2 * s),
                    Size(6 * s, 13 * s),
                    androidx.compose.ui.geometry.CornerRadius(3 * s),
                )
                drawArc(
                    color,
                    0f,
                    180f,
                    false,
                    Offset(5 * s, 7 * s),
                    Size(14 * s, 12 * s),
                    style = stroke
                )
                line(12f, 19f, 12f, 22f)
            }

            "home" -> {
                val path = Path().apply {
                    moveTo(3 * s, 11 * s); lineTo(12 * s, 3 * s); lineTo(
                    21 * s,
                    11 * s
                ); moveTo(5 * s, 10 * s); lineTo(5 * s, 21 * s); lineTo(
                    19 * s,
                    21 * s
                ); lineTo(19 * s, 10 * s)
                }
                drawPath(path, color, style = stroke)
                line(10f, 21f, 10f, 14f); line(10f, 14f, 14f, 14f); line(14f, 14f, 14f, 21f)
            }

            "arrow" -> {
                line(8f, 5f, 15f, 12f); line(15f, 12f, 8f, 19f)
            }

            "back" -> {
                line(11f, 5f, 4f, 12f); line(4f, 12f, 11f, 19f); line(4f, 12f, 21f, 12f)
            }
            "forward" -> {
                line(13f, 5f, 20f, 12f); line(20f, 12f, 13f, 19f); line(3f, 12f, 20f, 12f)
            }

            "check" -> {
                line(5f, 12f, 10f, 17f); line(10f, 17f, 20f, 6f)
            }

            "search" -> {
                drawCircle(color, 9 * s, Offset(10.5f * s, 10.5f * s), style = stroke)
                line(16.5f, 16.5f, 21f, 21f)
            }

            else -> {
                val marker = Path().apply {
                    moveTo(12*s, 23*s); cubicTo(9*s, 19*s, 3*s, 13*s, 3*s, 9*s)
                    cubicTo(3*s, -2*s, 21*s, -2*s, 21*s, 9*s)
                    cubicTo(21*s, 13*s, 15*s, 19*s, 12*s, 23*s); close()
                }
                drawPath(marker, color)
                drawCircle(Color.White, 3*s, Offset(12*s, 9*s))
            }
        }
    }
}
