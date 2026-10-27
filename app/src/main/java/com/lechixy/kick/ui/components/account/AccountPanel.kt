package com.lechixy.kick.ui.components.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lechixy.kick.data.account.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AccountPanel() {
    val auth = AccountGraph.auth
    val session by auth.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profileError by remember(session.generation) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kick hesabı", style = MaterialTheme.typography.titleLarge)
        session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (session.status) {
            SessionStatus.Authenticated -> {
                session.profile?.let { profile ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = profile.avatar, contentDescription = "Profil fotoğrafı", modifier = Modifier.size(48.dp))
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    }
                } ?: Text("Profil bilgisi henüz alınamadı.")
                profileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (session.profile == null) TextButton(onClick = {
                    scope.launch {
                        profileError = null
                        try { auth.loadProfile() }
                        catch (error: Exception) { if (error is CancellationException) throw error; profileError = error.userMessage() }
                    }
                }) { Text("Profili yeniden yükle") }
                Button(onClick = { scope.launch { auth.signOut() } }) { Text("Çıkış yap") }
            }
            SessionStatus.Connecting -> {
                Text("Kick girişi bekleniyor.")
                TextButton(onClick = { auth.cancelLogin() }) { Text("Girişi iptal et") }
            }
            else -> {
                Text("Hesap bağlamak isteğe bağlıdır. Yayınları giriş yapmadan izleyebilirsiniz.")
                Button(onClick = { auth.beginLogin(context) }) { Text("Kick ile giriş yap") }
            }
        }
    }
}

@Composable
fun AccountButton() {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.widthIn(max = 180.dp)) {
        Text(if (session.signedIn) session.profile?.name ?: "Hesabım" else "Giriş yap", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        text = { AccountPanel() }, confirmButton = { TextButton(onClick = { open = false }) { Text("Kapat") } })
}
