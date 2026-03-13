package com.cocro.ui.register

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
import com.cocro.ui.theme.FontTitle

@Composable
fun RegisterScreen(
    sessionState: SessionUiState,
    onRegister: (username: String, password: String, email: String?) -> Unit,
    onNavigateLogin: () -> Unit,
    onNavigateLanding: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    val isLoading = sessionState is SessionUiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CocroColors.paper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "CoCro",
            style = TextStyle(fontFamily = FontTitle, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-0.5).sp, color = CocroColors.ink),
        )
        Text(
            "Mots fléchés collaboratifs",
            style = MaterialTheme.typography.bodyMedium.copy(color = CocroColors.inkMuted),
        )
        Spacer(Modifier.height(40.dp))
        CocroHr()
        Spacer(Modifier.height(24.dp))

        Text("INSCRIPTION", style = MaterialTheme.typography.labelSmall.copy(color = CocroColors.inkMuted))
        Spacer(Modifier.height(16.dp))

        CocroTextField(value = username, onValueChange = { username = it }, label = "Pseudo (min. 3 caractères)", placeholder = "votre pseudo")
        Spacer(Modifier.height(12.dp))
        CocroTextField(value = password, onValueChange = { password = it }, label = "Mot de passe (min. 6 caractères)", placeholder = "••••••••", isPassword = true)
        Spacer(Modifier.height(12.dp))
        CocroTextField(value = email, onValueChange = { email = it }, label = "E-mail (optionnel)", placeholder = "vous@exemple.fr")

        if (sessionState is SessionUiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(sessionState.message, style = MaterialTheme.typography.bodySmall.copy(color = CocroColors.red))
        }

        Spacer(Modifier.height(20.dp))
        CocroButton(
            "Créer mon compte",
            onClick = { onRegister(username, password, email.ifBlank { null }) },
            modifier = Modifier.fillMaxWidth(),
            enabled = username.length >= 3 && password.length >= 6,
            loading = isLoading,
        )
        Spacer(Modifier.height(16.dp))
        CocroHr()
        Spacer(Modifier.height(16.dp))
        Text("Déjà un compte ?", style = MaterialTheme.typography.bodyMedium.copy(color = CocroColors.inkMuted))
        CocroButton("Se connecter", onNavigateLogin, modifier = Modifier.fillMaxWidth(), variant = CocroButtonVariant.Secondary)
        Spacer(Modifier.height(8.dp))
        CocroButton("← Revenir à l'accueil", onNavigateLanding, modifier = Modifier.fillMaxWidth(), variant = CocroButtonVariant.Ghost)
    }
}
