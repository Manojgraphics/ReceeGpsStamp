package com.receegpsstamp.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Brand gradients (gentle warm yellow → soft gold) ──
// Shared across the app so every screen uses the same look.
// Kept deliberately soft (low contrast) per the "simple & reliable" design leaning.

/** Diagonal brand gradient — for hero cards, primary buttons, selected states. */
val BrandGradient = Brush.linearGradient(listOf(AppYellow, AppAmber))

/** Horizontal brand gradient — for header strips / title bars. */
val BrandGradientH = Brush.horizontalGradient(listOf(AppYellow, AppAmber))

/** Very light gradient — for small icon chips / tiles (icons tinted AppYellowDark). */
val SoftChipGradient = Brush.linearGradient(listOf(YellowContainer, Color(0xFFFFE39A)))
