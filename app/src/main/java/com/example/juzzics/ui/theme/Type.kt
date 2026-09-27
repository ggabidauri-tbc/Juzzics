package com.example.juzzics.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = FontFamily.Default

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = Base,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

/** Confident, tight headings (screen titles, the song in the player) over calm body text. */
val Typography = Typography(
    displayLarge = style(52, 60, FontWeight.Bold, -1.0),
    displayMedium = style(44, 52, FontWeight.Bold, -0.8),
    displaySmall = style(36, 44, FontWeight.Bold, -0.5),
    headlineLarge = style(30, 38, FontWeight.Bold, -0.4),
    headlineMedium = style(26, 34, FontWeight.Bold, -0.3),
    headlineSmall = style(22, 30, FontWeight.SemiBold, -0.2),
    titleLarge = style(20, 28, FontWeight.SemiBold, -0.1),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.15),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.2),
    bodySmall = style(12, 16, FontWeight.Normal, 0.3),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.4),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5),
)
