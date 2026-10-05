package com.lechixy.kick.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lechixy.kick.data.local.FollowManager
import com.lechixy.kick.data.local.PlayerSettingsManager
import com.lechixy.kick.ui.components.HomeTopBar
import com.lechixy.kick.ui.components.StreamCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStreamClick: (channelSlug: String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val pullState = rememberPullToRefreshState()
    val followedSlugs by FollowManager.followedSlugs.collectAsState()

    val isDataSaverActive by PlayerSettingsManager.dataSaverEnabled.collectAsState()

    Scaffold(
        topBar = {
            HomeTopBar(
                onSearchClick = {},
                onSettingsClick = onSettingsClick
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pullState,
            indicator = {
                Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = state.isRefreshing,
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    color = MaterialTheme.colorScheme.primary,
                    state = pullState
                )
            }
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                else -> {
                    val normalizedFollowed = remember(followedSlugs) {
                        followedSlugs.map { it.trim().lowercase() }.toSet()
                    }

                    val followedStreams = remember(state.streams, normalizedFollowed) {
                        state.streams.filter { stream ->
                            val slug = stream.channel?.slug?.trim()?.lowercase() ?: ""
                            normalizedFollowed.contains(slug)
                        }
                    }

                    val otherStreams = remember(state.streams, normalizedFollowed) {
                        state.streams.filterNot { stream ->
                            val slug = stream.channel?.slug?.trim()?.lowercase() ?: ""
                            normalizedFollowed.contains(slug)
                        }
                    }

                    val categories = remember(state.streams) {
                        state.streams
                            .mapNotNull { it.category }
                            .distinctBy { it.id }
                            .sortedByDescending { category ->
                                state.streams.count { it.category?.id == category.id }
                            }
                            .take(10)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (followedStreams.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "FOLLOWED CHANNELS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(followedStreams, key = { "followed_${it.id}" }) { stream ->
                                StreamCard(
                                    stream = stream,
                                    isDataSaverActive = false, // Takip edilenlerde her zaman resim yüklenir
                                    onClick = { stream.channel?.slug?.let(onStreamClick) }
                                )
                            }
                        }

                        if (categories.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "POPULAR CATEGORIES",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(categories, key = { it.id }) { cat ->
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(cat.name) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "LIVE NOW",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(otherStreams, key = { it.id }) { stream ->
                            StreamCard(
                                stream = stream,
                                isDataSaverActive = isDataSaverActive, // Tasarruf modu açıksa resim yerine Tasarruf Modu yazar
                                onClick = { stream.channel?.slug?.let(onStreamClick) }
                            )
                        }
                    }
                }
            }
        }
    }
}