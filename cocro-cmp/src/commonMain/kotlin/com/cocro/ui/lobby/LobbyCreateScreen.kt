package com.cocro.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.cocro.ui.components.CocroHr
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocro.session.SessionUiState
import com.cocro.ui.components.CocroButton
import com.cocro.ui.components.CocroButtonVariant
import com.cocro.ui.components.CocroTextField
import com.cocro.ui.theme.CocroColors
import com.cocro.ui.theme.FontLabel
import com.cocro.ui.theme.FontTitle

@Composable
fun LobbyCreateScreen(
    sessionState: SessionUiState,
    onCreateSession: (gridId: String) -> Unit,
    onBack: () -> Unit,
) {
    var gridId by remember { mutableStateOf("") }
    val isLoading = sessionState is SessionUiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CocroColors.paper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("CoCro", style = TextStyle(fontFamily = FontTitle, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = CocroColors.ink))
        Spacer(Modifier.height(32.dp))
        CocroHr()
        Spacer(Modifier.height(24.dp))

        Text(
            "CRÉER UNE SESSION".uppercase(),
            style = TextStyle(fontFamily = FontLabel, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp, color = CocroColors.inkMuted),
        )
        Spacer(Modifier.height(8.dp))
        Text("Choisissez une grille", style = TextStyle(fontFamily = FontTitle, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = CocroColors.ink))
        Spacer(Modifier.height(24.dp))

        CocroTextField(
            value = gridId,
            onValueChange = { gridId = it },
            label = "Identifiant de grille",
            placeholder = "ex : abc123",
        )

        if (sessionState is SessionUiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(sessionState.message, style = MaterialTheme.typography.bodySmall.copy(color = CocroColors.red))
        }

        Spacer(Modifier.height(20.dp))
        CocroButton(
            "Créer la session",
            onClick = { onCreateSession(gridId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = gridId.isNotBlank(),
            loading = isLoading,
        )
        Spacer(Modifier.height(8.dp))
        CocroButton("← Retour", onBack, modifier = Modifier.fillMaxWidth(), variant = CocroButtonVariant.Ghost)
    }
}
