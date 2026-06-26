package com.receegpsstamp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RgsTypography = Typography(
    displaySmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).sp),
    headlineSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
)
