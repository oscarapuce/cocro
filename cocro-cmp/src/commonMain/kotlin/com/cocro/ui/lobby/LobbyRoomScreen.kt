package com.cocro.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.cocro.ui.components.CocroHr
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocro.session.SessionStatus
import com.cocro.session.SessionUiState
import com.cocro.session.canStart
import com.cocro.ui.components.CocroButton
import com.cocro.ui.components.CocroButtonVariant
import com.cocro.ui.theme.CocroColors
import com.cocro.ui.theme.FontLabel
import com.cocro.ui.theme.FontTitle

@Composable
fun LobbyRoomScreen(
    shareCode: String,
    sessionState: SessionUiState,
    onStartSession: () -> Unit,
    onLeaveSession: () -> Unit,
) {
    val active = sessionState as? SessionUiState.Active
    val isLoading = sessionState is SessionUiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CocroColors.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        // Navbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CoCro", style = TextStyle(fontFamily = FontTitle, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = CocroColors.ink))
            CocroButton("Quitter", onLeaveSession, variant = CocroButtonVariant.Danger)
        }
        CocroHr(horizontalPadding = 24.dp)
        Spacer(Modifier.height(32.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Share code
            Text("CODE DE PARTAGE", style = TextStyle(fontFamily = FontLabel, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = CocroColors.inkMuted))
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .border(1.dp, CocroColors.border, RectangleShape)
                    .background(CocroColors.surface)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    shareCode,
                    style = TextStyle(fontFamily = FontTitle, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 4.sp, color = CocroColors.forest),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Partagez ce code avec vos amis",
                style = MaterialTheme.typography.bodySmall.copy(color = CocroColors.inkMuted),
            )

            Spacer(Modifier.height(32.dp))
            CocroHr()
            Spacer(Modifier.height(24.dp))

            // Status
            if (active != null) {
                Text(
                    "STATUT",
                    style = TextStyle(fontFamily = FontLabel, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = CocroColors.inkMuted),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when (active.status) {
                        SessionStatus.CREATING -> "En attente de joueurs…"
                        SessionStatus.PLAYING  -> "Partie en cours"
                        else -> active.status.name
                    },
                    style = MaterialTheme.typography.titleMedium.copy(color = CocroColors.forest),
                )

                Spacer(Modifier.height(24.dp))

                // Participants slots (4 max)
                Text(
                    "PARTICIPANTS (${active.participants.size}/4)",
                    style = TextStyle(fontFamily = FontLabel, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = CocroColors.inkMuted),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { index ->
                        val participant = active.participants.getOrNull(index)
                        ParticipantSlot(
                            label = participant?.userId?.take(8) ?: "—",
                            filled = participant != null,
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                if (active.canStart()) {
                    CocroButton("Démarrer la partie", onStartSession, modifier = Modifier.fillMaxWidth(), loading = isLoading)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (sessionState is SessionUiState.Loading) {
                Text(sessionState.message, style = MaterialTheme.typography.bodyMedium.copy(color = CocroColors.inkMuted))
            }
        }
    }
}

@Composable
private fun ParticipantSlot(label: String, filled: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, if (filled) CocroColors.forest else CocroColors.borderSoft, RectangleShape)
                .background(if (filled) CocroColors.forest.copy(alpha = 0.08f) else CocroColors.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (filled) label.first().uppercaseChar().toString() else "+",
                style = TextStyle(fontFamily = FontTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (filled) CocroColors.forest else CocroColors.borderSoft),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (filled) label else "Libre",
            style = TextStyle(fontFamily = FontLabel, fontSize = 9.sp, letterSpacing = 0.5.sp, color = if (filled) CocroColors.ink else CocroColors.borderSoft),
        )
    }
}
