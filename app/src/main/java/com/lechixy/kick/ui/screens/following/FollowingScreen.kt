package com.lechixy.kick.ui.screens.following

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lechixy.kick.data.local.FollowManager

@Composable
fun FollowingScreen(
    viewModel: FollowingViewModel = viewModel(),
    onStreamClick: (channelSlug: String) -> Unit
) {
    val context = LocalContext.current
    val followedSlugs by FollowManager.followedSlugs.collectAsState()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(followedSlugs) {
        viewModel.loadFollowingChannels(followedSlugs)
    }

    val online = state.online
    val offline = state.offline

    Scaffold(
        topBar = {
            Text(
                text = "Following",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            followedSlugs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Henüz takip ettiğin bir kanal yok.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Online Channels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (online.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "There is no live channels from followed channels.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
//                        items(liveChannels, key = { "followed_${it.id}" }) { channel ->
//                            StreamCard(
//                                stream = channel,
//                                onClick = { channel.channel?.slug?.let(onStreamClick) }
//                            )
//                        }
                    }
//                    item(span = { GridItemSpan(maxLineSpan) }) {
//                        Text(
//                            text = "Offline Channels",
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold,
//                            color = MaterialTheme.colorScheme.outline
//                        )
//                    }
//                    items(channels, key = { it.id }) { channel ->
//                        ChannelCard(
//                            channel = channel,
//                            onChannelClick = {
//                                channel.slug.let(onStreamClick)
//                            }
//                        )
//                    }
                }
            }
        }
    }
}