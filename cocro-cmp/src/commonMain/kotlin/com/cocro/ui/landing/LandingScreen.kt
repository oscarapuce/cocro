package com.cocro.ui.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocro.session.SessionUiState
import com.cocro.ui.components.CocroButton
import com.cocro.ui.components.CocroButtonVariant
import com.cocro.ui.components.CocroHr
import com.cocro.ui.components.CocroTextField
import com.cocro.ui.theme.CocroColors
import com.cocro.ui.theme.FontBody
import com.cocro.ui.theme.FontLabel
import com.cocro.ui.theme.FontTitle
import com.cocro.ui.theme.FontUi

@Composable
fun LandingScreen(
    isAnonymous: Boolean,
    guestUsername: String?,
    sessionState: SessionUiState,
    onJoinAsGuest: (shareCode: String) -> Unit,
    onJoinAuthenticated: (shareCode: String) -> Unit,
    onNavigateLogin: () -> Unit,
    onNavigateRegister: () -> Unit,
) {
    var shareCode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf("") }
    val isLoading = sessionState is SessionUiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CocroColors.paper)
            .verticalScroll(rememberScrollState()),
    ) {

        // ── Navbar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CoCro",
                style = TextStyle(
                    fontFamily = FontTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    letterSpacing = (-0.5).sp,
                    color = CocroColors.ink,
                ),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isAnonymous && guestUsername != null) {
                    Text(
                        text = "Invité · $guestUsername",
                        style = TextStyle(
                            fontFamily = FontUi,
                            fontSize = 12.sp,
                            color = CocroColors.inkMuted,
                        ),
                    )
                }
                CocroButton(
                    text = "Connexion",
                    onClick = onNavigateLogin,
                    variant = CocroButtonVariant.Ghost,
                )
                CocroButton(
                    text = "Inscription",
                    onClick = onNavigateRegister,
                    variant = CocroButtonVariant.Secondary,
                )
            }
        }

        // ── 1px separator (magazine style) ───────────────────────────────────
        CocroHr(horizontalPadding = 24.dp)

        // ── Hero ──────────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp)) {
            Text(
                text = "L'Atelier\ndu Cruciverbiste",
                style = TextStyle(
                    fontFamily = FontTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.8).sp,
                    color = CocroColors.ink,
                ),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Mots fléchés collaboratifs — jouez à plusieurs en temps réel.",
                style = TextStyle(
                    fontFamily = FontBody,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    color = CocroColors.inkMuted,
                ),
            )
        }

        CocroHr(horizontalPadding = 24.dp)

        // ── Panel Rejoindre (white) ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CocroColors.surface)
                .padding(24.dp),
        ) {
            SectionLabel("Rejoindre une partie")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Vous avez un code ?",
                style = TextStyle(
                    fontFamily = FontTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp,
                    color = CocroColors.ink,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isAnonymous && guestUsername != null)
                    "Vous jouez en tant qu'invité — $guestUsername"
                else
                    "Entrez le code partagé par l'hôte pour rejoindre.",
                style = TextStyle(
                    fontFamily = FontBody,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = CocroColors.inkMuted,
                ),
            )
            Spacer(Modifier.height(18.dp))
            CocroTextField(
                value = shareCode,
                onValueChange = { shareCode = it.uppercase(); joinError = "" },
                label = "Code de partage",
                placeholder = "ABC123",
                isError = joinError.isNotEmpty() || sessionState is SessionUiState.Error,
                errorMessage = if (joinError.isNotEmpty()) joinError
                else if (sessionState is SessionUiState.Error) sessionState.message else "",
            )
            Spacer(Modifier.height(14.dp))
            CocroButton(
                text = "Rejoindre →",
                onClick = {
                    if (shareCode.length < 4) {
                        joinError = "Code requis (4 caractères min.)"
                        return@CocroButton
                    }
                    if (isAnonymous) onJoinAuthenticated(shareCode)
                    else onJoinAsGuest(shareCode)
                },
                modifier = Modifier.fillMaxWidth(),
                loading = isLoading,
            )
        }

        // ── Panel Créer un compte (forest green) ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CocroColors.forest)
                .padding(24.dp),
        ) {
            SectionLabel("Créer votre atelier", light = true)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Concevez, invitez, jouez.",
                style = TextStyle(
                    fontFamily = FontTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.3).sp,
                    color = Color.White,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Créez vos propres grilles de mots fléchés et lancez des sessions collaboratives avec vos amis.",
                style = TextStyle(
                    fontFamily = FontBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.78f),
                ),
            )
            Spacer(Modifier.height(20.dp))
            CocroButton(
                text = "Créer un compte",
                onClick = onNavigateRegister,
                modifier = Modifier.fillMaxWidth(),
                variant = CocroButtonVariant.Secondary,
            )
            Spacer(Modifier.height(8.dp))
            CocroButton(
                text = "Déjà un compte ? Se connecter",
                onClick = onNavigateLogin,
                modifier = Modifier.fillMaxWidth(),
                variant = CocroButtonVariant.Ghost,
            )
        }

        // ── Footer ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CoCro",
                style = TextStyle(
                    fontFamily = FontLabel,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = CocroColors.inkMuted,
                ),
            )
            Text(
                text = "Mots fléchés collaboratifs",
                style = TextStyle(
                    fontFamily = FontLabel,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = CocroColors.borderSoft,
                ),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, light: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            fontFamily = FontLabel,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = if (light) Color.White.copy(alpha = 0.55f) else CocroColors.inkMuted,
        ),
    )
}
