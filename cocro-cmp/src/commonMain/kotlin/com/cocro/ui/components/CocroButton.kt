package com.cocro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocro.ui.theme.CocroColors
import com.cocro.ui.theme.LocalCocroFonts

enum class CocroButtonVariant { Primary, Secondary, Ghost, Danger }

/**
 * Offset shadow modifier (style cahier Fusion B).
 * Reserves 3dp bottom/right and draws a shifted rectangle behind the button.
 */
private fun Modifier.offsetShadow(shadowColor: Color): Modifier =
    this
        .padding(end = 3.dp, bottom = 3.dp)
        .drawBehind {
            drawRect(
                color = shadowColor,
                topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                size = size,
            )
        }

@Composable
fun CocroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CocroButtonVariant = CocroButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val fonts = LocalCocroFonts.current
    val ctaStyle = TextStyle(fontFamily = fonts.hand, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.3.sp)
    val ghostStyle = TextStyle(fontFamily = fonts.ui, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.3.sp)

    when (variant) {
        CocroButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.offsetShadow(CocroColors.forestDark).fillMaxWidth().height(48.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = CocroColors.forest,
                contentColor = Color.White,
                disabledContainerColor = CocroColors.forest.copy(alpha = 0.45f),
                disabledContentColor = Color.White.copy(alpha = 0.45f),
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            else Text(text, style = ctaStyle)
        }

        CocroButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.offsetShadow(CocroColors.ink).fillMaxWidth().height(48.dp),
            shape = RectangleShape,
            border = BorderStroke(2.dp, CocroColors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CocroColors.surface,
                contentColor = CocroColors.ink,
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            if (loading) CircularProgressIndicator(color = CocroColors.ink, strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            else Text(text, style = ctaStyle.copy(color = CocroColors.ink))
        }

        CocroButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.height(48.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.textButtonColors(contentColor = CocroColors.ink),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            Text(text, style = ghostStyle.copy(color = CocroColors.ink))
        }

        CocroButtonVariant.Danger -> OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.offsetShadow(CocroColors.red).fillMaxWidth().height(48.dp),
            shape = RectangleShape,
            border = BorderStroke(2.dp, CocroColors.red),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = CocroColors.red,
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            Text(text, style = ctaStyle.copy(color = CocroColors.red))
        }
    }
}
