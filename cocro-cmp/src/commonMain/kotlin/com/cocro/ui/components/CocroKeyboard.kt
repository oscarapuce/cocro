package com.cocro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocro.ui.theme.CocroColors
import com.cocro.ui.theme.LocalCocroFonts

enum class WordDirection { HORIZONTAL, VERTICAL }

private val AZERTY_ROW_1 = "AZERTYUIOP"
private val AZERTY_ROW_2 = "QSDFGHJKLM"
private val AZERTY_ROW_3 = "WXCVBN"

@Composable
fun CocroKeyboard(
    direction: WordDirection,
    onLetterInput: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDirectionToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalCocroFonts.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CocroColors.surfaceAlt)
            .drawBehind {
                // 2dp dashed border — drawn as a solid approximation (Compose has no native dashed border)
                val stroke = 2.dp.toPx()
                drawRect(
                    color = CocroColors.borderSoft,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
            }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AZERTY_ROW_1.forEach { char ->
                KeyButton(text = char.toString(), modifier = Modifier.weight(1f), fonts = fonts, onClick = { onLetterInput(char) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AZERTY_ROW_2.forEach { char ->
                KeyButton(text = char.toString(), modifier = Modifier.weight(1f), fonts = fonts, onClick = { onLetterInput(char) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyButton(
                text = if (direction == WordDirection.HORIZONTAL) "→" else "↓",
                modifier = Modifier.weight(1.5f),
                accent = true,
                fonts = fonts,
                onClick = onDirectionToggle,
            )
            AZERTY_ROW_3.forEach { char ->
                KeyButton(text = char.toString(), modifier = Modifier.weight(1f), fonts = fonts, onClick = { onLetterInput(char) })
            }
            KeyButton(text = "⌫", modifier = Modifier.weight(1.5f), fonts = fonts, onClick = onBackspace)
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    fonts: com.cocro.ui.theme.CocroFontFamilies,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val shadowColor = if (accent) CocroColors.forestDark else CocroColors.borderSoft
    Box(
        modifier = modifier
            .padding(end = 2.dp, bottom = 2.dp)
            .drawBehind {
                drawRect(
                    color = shadowColor,
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = size,
                )
            }
            .height(38.dp)
            .border(
                width = 1.dp,
                color = if (accent) CocroColors.forest else CocroColors.borderSoft,
                shape = RectangleShape,
            )
            .background(if (accent) CocroColors.forest else CocroColors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = fonts.body,  // Lexend — per spec
                fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                color = if (accent) androidx.compose.ui.graphics.Color.White else CocroColors.ink,
            ),
        )
    }
}
