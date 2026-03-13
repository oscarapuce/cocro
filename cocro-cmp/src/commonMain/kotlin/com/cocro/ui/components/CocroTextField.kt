package com.cocro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocro.ui.theme.CocroColors
import com.cocro.ui.theme.LocalCocroFonts

@Composable
fun CocroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    val fonts = LocalCocroFonts.current
    var focused by remember { mutableStateOf(false) }
    val lineColor = when {
        isError -> CocroColors.red
        focused -> CocroColors.forest
        else    -> CocroColors.borderSoft
    }
    val lineWidth = if (focused || isError) 2.dp else 1.5.dp

    Column(modifier = modifier) {
        // Label (Spline Sans spirit → small caps uppercase)
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = fonts.label,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = CocroColors.inkMuted,
            ),
        )
        Spacer(Modifier.height(4.dp))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            textStyle = TextStyle(
                fontFamily = fonts.body,
                fontSize = 16.sp,
                color = CocroColors.ink,
            ),
            cursorBrush = SolidColor(CocroColors.forest),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Style underline (cahier Séyès) — bord bas uniquement
                    val strokePx = lineWidth.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, size.height - strokePx / 2f),
                        end = Offset(size.width, size.height - strokePx / 2f),
                        strokeWidth = strokePx,
                    )
                }
                .padding(horizontal = 4.dp, vertical = 10.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerField ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        style = TextStyle(
                            fontFamily = fonts.body,
                            fontSize = 16.sp,
                            color = CocroColors.inkMuted,
                        ),
                    )
                }
                innerField()
            },
        )

        if (isError && errorMessage.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = TextStyle(fontFamily = fonts.body, fontSize = 12.sp, color = CocroColors.red),
            )
        }
    }
}
