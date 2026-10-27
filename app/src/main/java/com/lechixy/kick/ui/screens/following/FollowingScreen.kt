package com.lechixy.kick.ui.screens.following

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.SessionStatus
import com.lechixy.kick.ui.components.ExpansionSource
import com.lechixy.kick.ui.components.account.FollowedChannelCard

@Composable
fun FollowingScreen(
    viewModel: FollowingViewModel = viewModel(),
    onStreamClick: (channelSlug: String, source: ExpansionSource?) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text("Takip edilenler", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
        if (!session.signedIn) {
            Text("Takip etmek için giriş yapın.")
            session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (session.status == SessionStatus.Connecting) {
                TextButton(onClick = { AccountGraph.auth.cancelLogin() }) { Text("Girişi iptal et") }
            } else Button(onClick = { AccountGraph.auth.beginLogin(context) }) { Text("Kick ile giriş yap") }
        } else {
            TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Yenile") }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loaded && state.channels.isEmpty()) Text("Henüz takip ettiğin bir kanal yok.")
            LazyVerticalGrid(columns = GridCells.Adaptive(280.dp), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val online = state.channels.filter { it.live }
                val offline = state.channels.filterNot { it.live }
                if (online.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Canlı kanallar") }
                items(online, key = { it.slug }) { channel ->
                    FollowedChannelCard(channel) { onStreamClick(channel.slug, null) }
                }
                if (offline.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Çevrimdışı kanallar") }
                items(offline, key = { it.slug }) { channel ->
                    FollowedChannelCard(channel) { onStreamClick(channel.slug, null) }
                }
                if (state.nextCursor != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = viewModel::loadMore, enabled = !state.loading) { Text("Daha fazla yükle") }
                }
            }
        }
    }
}
