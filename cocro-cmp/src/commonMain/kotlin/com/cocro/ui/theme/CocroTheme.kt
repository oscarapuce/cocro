package com.cocro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val CocroColorScheme = lightColorScheme(
    primary              = CocroColors.forest,
    onPrimary            = Color.White,
    primaryContainer     = CocroColors.forestLight,
    onPrimaryContainer   = CocroColors.ink,
    secondary            = CocroColors.forestLight,
    onSecondary          = CocroColors.ink,
    background           = CocroColors.paper,
    onBackground         = CocroColors.ink,
    surface              = CocroColors.surface,
    onSurface            = CocroColors.ink,
    surfaceVariant       = CocroColors.surfaceAlt,
    onSurfaceVariant     = CocroColors.inkMuted,
    error                = CocroColors.red,
    onError              = Color.White,
    outline              = CocroColors.border,
    outlineVariant       = CocroColors.borderSoft,
)

@Composable
fun CocroTheme(content: @Composable () -> Unit) {
    val fonts = rememberCocroFontFamilies()
    CompositionLocalProvider(LocalCocroFonts provides fonts) {
        MaterialTheme(
            colorScheme = CocroColorScheme,
            typography  = cocroTypography(fonts),
            content     = content,
        )
    }
}
