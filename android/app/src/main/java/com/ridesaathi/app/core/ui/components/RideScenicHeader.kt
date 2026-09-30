package com.ridesaathi.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.ridesaathi.app.core.ui.theme.RideColors

/** Decorative normalized artwork. Replace this component with final vectors without changing screens. */
@Composable
internal fun RideScenicHeader(home: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(156.dp)) {
        scale(size.width / 360f, size.height / 156f, pivot = Offset.Zero) {
            drawRoundRect(Brush.verticalGradient(listOf(RideColors.Sky, RideColors.Cream), endY = 150f), size = Size(360f,156f), cornerRadius = CornerRadius(24f))
            fun cloud(x: Float, y: Float) {
                drawCircle(Color.White, 12f, Offset(x,y)); drawCircle(Color.White,18f,Offset(x+17,y-7))
                drawRoundRect(Color.White,Offset(x-10,y),Size(58f,12f),CornerRadius(6f))
            }
            cloud(50f,33f); cloud(260f,24f)
            listOf(170f,197f,229f,300f).forEachIndexed { i,x ->
                val h=42f+i*11f
                drawRoundRect(if(i%2==0) RideColors.Sky.copy(red=.72f) else RideColors.Peach,Offset(x,123-h),Size(23f,h),CornerRadius(3f))
                repeat(3) { n -> drawRect(Color.White.copy(alpha=.8f),Offset(x+7,130-h+n*12),Size(5f,6f)) }
            }
            drawOval(RideColors.Mint,Offset(-30f,102f),Size(430f,90f))
            drawPath(Path().apply { moveTo(0f,143f); cubicTo(95f,104f,210f,116f,360f,137f); lineTo(360f,156f); lineTo(0f,156f); close() },RideColors.Road)
            drawLine(Color.White,Offset(20f,148f),Offset(340f,148f),2f)
            fun tree(x: Float,y: Float,r: Float) {
                drawLine(RideColors.Trunk,Offset(x,y),Offset(x,y+50),5f,StrokeCap.Round)
                drawCircle(RideColors.Leaf,r,Offset(x,y)); drawCircle(RideColors.Emerald.copy(alpha=.7f),r*.72f,Offset(x-9,y+9))
                drawCircle(RideColors.Leaf.copy(green=.79f),r*.7f,Offset(x+10,y-9))
            }
            tree(20f,80f,24f); tree(340f,71f,28f); tree(153f,94f,15f)
            if(home) {
                drawRect(RideColors.Peach,Offset(58f,84f),Size(66f,48f))
                drawPath(Path().apply { moveTo(48f,86f); lineTo(91f,49f); lineTo(134f,86f); close() },RideColors.Orange)
                drawRect(RideColors.Trunk,Offset(83f,102f),Size(17f,30f))
                drawRect(RideColors.Sky,Offset(106f,96f),Size(12f,14f))
            }
            // Friendly compact car with visible wheels and windscreen.
            drawRoundRect(Color.White,Offset(225f,117f),Size(72f,22f),CornerRadius(9f))
            drawPath(Path().apply { moveTo(239f,118f); lineTo(250f,102f); lineTo(274f,102f); lineTo(287f,118f); close() },Color.White)
            drawPath(Path().apply { moveTo(246f,116f); lineTo(253f,106f); lineTo(271f,106f); lineTo(280f,116f); close() },RideColors.Navy)
            drawCircle(RideColors.Navy,7f,Offset(239f,138f)); drawCircle(RideColors.Navy,7f,Offset(283f,138f))
            drawCircle(RideColors.Road,3f,Offset(239f,138f)); drawCircle(RideColors.Road,3f,Offset(283f,138f))
        }
    }
}
