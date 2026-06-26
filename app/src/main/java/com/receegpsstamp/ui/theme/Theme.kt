package com.receegpsstamp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RgsColorScheme = lightColorScheme(
    primary          = BrandGrey,
    onPrimary        = Color.White,
    secondary        = AppYellow,
    onSecondary      = Color.Black,
    background       = NeutralBg,
    surface          = NeutralSurface,
    onBackground     = NeutralText,
    onSurface        = NeutralText,
    outline          = NeutralOutline,
    outlineVariant   = NeutralOutlineV,
    error            = StatusError,
    surfaceVariant   = NeutralSurfaceV,
    secondaryContainer = YellowContainer,
    onSecondaryContainer = YellowOnContainer,
)

@Composable
fun RgsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RgsColorScheme,
        typography = RgsTypography,
        content = content,
    )
}
