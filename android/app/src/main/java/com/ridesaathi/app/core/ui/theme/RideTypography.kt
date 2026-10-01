package com.ridesaathi.app.core.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RideTypography = Typography(
            headlineLarge = TextStyle(
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold
            ),
            headlineMedium = TextStyle(
                fontSize = 27.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            titleLarge = TextStyle(
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold
            ),
            titleMedium = TextStyle(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            ),
            bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
