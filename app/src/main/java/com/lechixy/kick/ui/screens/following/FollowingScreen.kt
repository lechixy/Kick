package com.lechixy.kick.ui.screens.following

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lechixy.kick.ui.components.ChannelCard
import com.lechixy.kick.ui.components.ChannelCardSkeleton
import com.lechixy.kick.ui.components.ExpansionSource
import com.lechixy.kick.ui.components.SkeletonLoadingScope
import com.lechixy.kick.ui.components.StreamCard
import com.lechixy.kick.ui.components.StreamCardSkeleton
import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.SessionStatus
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelUser

@Composable
fun FollowingScreen(
    viewModel: FollowingViewModel = viewModel(),
    onStreamClick: (channelSlug: String, source: ExpansionSource?) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val offline = remember(state.pageChannels) { state.pageChannels.filterNot { it.live } }

    SkeletonLoadingScope(active = session.signedIn &&
        ((state.streamsLoading && state.livestreams.isEmpty()) || (state.loading && offline.isEmpty()))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.following_title), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                if (session.signedIn) {
                    TextButton(onClick = viewModel::refresh, enabled = !state.loading && !state.streamsLoading) {
                        Text(stringResource(R.string.following_refresh))
                    }
                }
            }
            if (!session.signedIn) {
                Text("Takip etmek için giriş yapın.")
                session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (session.status == SessionStatus.Connecting) {
                    TextButton(onClick = { AccountGraph.auth.cancelLogin() }) { Text("Girişi iptal et") }
                } else Button(onClick = { AccountGraph.auth.beginLogin(context) }) { Text("Kick ile giriş yap") }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(280.dp), state = gridState,
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "online_header", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.following_online), style = MaterialTheme.typography.titleLarge)
                            state.streamsError?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = viewModel::retryStreams) { Text(stringResource(R.string.following_retry)) }
                            }
                            if (state.streamsLoaded && state.livestreams.isEmpty() && !state.streamsLoading) {
                                Text(stringResource(R.string.following_no_online), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.streamsLoading && state.livestreams.isEmpty()) {
                        items(2, key = { "live_skeleton_$it" }) { StreamCardSkeleton() }
                    }
                    items(state.livestreams, key = { "live_${it.id}" }) { stream ->
                        val slug = stream.channel?.slug.orEmpty()
                        StreamCard(stream = stream, onClick = { onStreamClick(slug, null) },
                            onClickWithSource = { source -> onStreamClick(slug, source) })
                    }
                    item(key = "offline_header", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.following_offline), style = MaterialTheme.typography.titleLarge)
                            state.error?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = viewModel::retryChannels) { Text(stringResource(R.string.following_retry)) }
                            }
                            if (state.loaded && offline.isEmpty() && !state.loading) {
                                Text(stringResource(R.string.following_no_offline), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.loading && offline.isEmpty()) {
                        items(6, key = { "offline_skeleton_$it" }) { ChannelCardSkeleton() }
                    }
                    items(offline, key = { "offline_${it.slug}" }) { channel ->
                        val detail = remember(channel) {
                            ChannelDetail(id = 0, slug = channel.slug,
                                user = ChannelUser(id = 0, username = channel.username, profilePic = channel.avatar))
                        }
                        ChannelCard(channel = detail, modifier = Modifier.fillMaxWidth(),
                            onChannelClick = { onStreamClick(it, null) }, onChannelClickWithSource = onStreamClick)
                    }
                    if (state.loaded && (state.pageIndex > 0 || state.nextCursor != null)) {
                        item(key = "pagination", span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.following_page, state.pageIndex + 1),
                                    style = MaterialTheme.typography.labelLarge)
                                val previousLabel = stringResource(R.string.following_previous)
                                val nextLabel = stringResource(R.string.following_next)
                                ButtonGroup(overflowIndicator = { menuState ->
                                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                                }) {
                                    clickableItem(onClick = viewModel::previousPage, label = previousLabel,
                                        enabled = state.pageIndex > 0 && !state.loading)
                                    clickableItem(onClick = viewModel::loadMore, label = nextLabel,
                                        enabled = state.nextCursor != null && !state.loading)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
