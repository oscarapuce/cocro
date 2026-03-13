package com.cocro.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun HomeScreen(
    username: String,
    isAnonymous: Boolean,
    sessionState: SessionUiState,
    onJoinSession: (shareCode: String) -> Unit,
    onCreateSession: () -> Unit,
    onCreateGrid: () -> Unit,
    onLogout: () -> Unit,
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = username,
                    style = TextStyle(
                        fontFamily = FontUi,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = CocroColors.inkMuted,
                    ),
                )
                CocroButton(
                    text = "Déconnexion",
                    onClick = onLogout,
                    variant = CocroButtonVariant.Ghost,
                )
            }
        }

        CocroHr(horizontalPadding = 24.dp)

        // ── Hero ──────────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
            Text(
                text = "Bonjour,\n$username",
                style = TextStyle(
                    fontFamily = FontTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.8).sp,
                    color = CocroColors.ink,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Rejoignez une session, lancez la vôtre ou concevez une nouvelle grille.",
                style = TextStyle(
                    fontFamily = FontBody,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
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
            SectionLabel("Rejoindre")
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
                text = "Entrez le code de session partagé par l'hôte.",
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
                    onJoinSession(shareCode)
                },
                modifier = Modifier.fillMaxWidth(),
                loading = isLoading,
            )
        }

        // ── Panel Créer une session (forest green) ────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CocroColors.forest)
                .padding(24.dp),
        ) {
            SectionLabel("Lancer une session", light = true)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Créer une session",
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
                text = "Sélectionnez une grille et invitez jusqu'à 4 participants à jouer en temps réel.",
                style = TextStyle(
                    fontFamily = FontBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.78f),
                ),
            )
            Spacer(Modifier.height(20.dp))
            CocroButton(
                text = "Nouvelle session →",
                onClick = onCreateSession,
                modifier = Modifier.fillMaxWidth(),
                variant = CocroButtonVariant.Secondary,
            )
        }

        // ── Panel Créer une grille (surfaceAlt) ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CocroColors.surfaceAlt)
                .padding(24.dp),
        ) {
            SectionLabel("Édition")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Créer une grille",
                style = TextStyle(
                    fontFamily = FontTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp,
                    color = CocroColors.ink,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Concevez vos propres mots fléchés — définissez les cases, les mots et les indices.",
                style = TextStyle(
                    fontFamily = FontBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = CocroColors.inkMuted,
                ),
            )
            Spacer(Modifier.height(20.dp))
            CocroButton(
                text = "Éditeur de grille →",
                onClick = onCreateGrid,
                modifier = Modifier.fillMaxWidth(),
                variant = CocroButtonVariant.Secondary,
            )
        }

        CocroHr(horizontalPadding = 24.dp)

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
