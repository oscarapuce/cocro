package com.cocro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cocro.cocro_cmp.generated.resources.Lexend_Regular
import cocro.cocro_cmp.generated.resources.PatrickHand_Regular
import cocro.cocro_cmp.generated.resources.PlusJakartaSans_Medium
import cocro.cocro_cmp.generated.resources.Res
import cocro.cocro_cmp.generated.resources.SpaceGrotesk_Bold
import cocro.cocro_cmp.generated.resources.SplineSans_Regular
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

data class CocroFontFamilies(
    val title: FontFamily,  // Space Grotesk 700
    val hand: FontFamily,   // Patrick Hand — CTA buttons
    val ui: FontFamily,     // Plus Jakarta Sans — menus, ghost buttons
    val body: FontFamily,   // Lexend — body text, grid cells
    val label: FontFamily,  // Spline Sans — small uppercase labels
)

/** Fallback families used before real fonts are loaded (previews, tests). */
private val FallbackFonts = CocroFontFamilies(
    title = FontFamily.SansSerif,
    hand  = FontFamily.Cursive,
    ui    = FontFamily.SansSerif,
    body  = FontFamily.SansSerif,
    label = FontFamily.SansSerif,
)

val LocalCocroFonts = staticCompositionLocalOf { FallbackFonts }

@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberCocroFontFamilies() = CocroFontFamilies(
    title = FontFamily(Font(Res.font.SpaceGrotesk_Bold, FontWeight.Bold)),
    hand  = FontFamily(Font(Res.font.PatrickHand_Regular, FontWeight.Normal)),
    ui    = FontFamily(Font(Res.font.PlusJakartaSans_Medium, FontWeight.Medium)),
    body  = FontFamily(Font(Res.font.Lexend_Regular, FontWeight.Normal)),
    label = FontFamily(Font(Res.font.SplineSans_Regular, FontWeight.Normal)),
)

@Composable
fun cocroTypography(fonts: CocroFontFamilies) = Typography(
    displayLarge = TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fonts.ui,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = fonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = fonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = fonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = fonts.ui,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = fonts.label,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
    ),
)

// Convenience top-level vals — delegate to LocalCocroFonts in @Composable contexts
// Keep these as fallbacks for non-composable usage
val FontTitle  = FallbackFonts.title
val FontHand   = FallbackFonts.hand
val FontUi     = FallbackFonts.ui
val FontBody   = FallbackFonts.body
val FontLabel  = FallbackFonts.label
val FontGrid   = FallbackFonts.body
