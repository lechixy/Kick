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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.ui.components.account.AccountFollowingSection
import androidx.compose.foundation.layout.fillMaxWidth
import com.lechixy.kick.ui.components.ExpansionSource
import com.lechixy.kick.ui.components.HomeTopBar
import com.lechixy.kick.ui.components.StreamCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onStreamClick: (channelSlug: String, source: ExpansionSource?) -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val pullState = rememberPullToRefreshState()

    val isDataSaverActive = false

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
            onRefresh = { viewModel.refresh(); AccountGraph.following.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = pullState,
            indicator = {
                Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = state.isRefreshing,
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    color = MaterialTheme.colorScheme.primary,
                    state = pullState
                )
            }
        ) {
            val streams = remember(state.streams) { state.streams.distinctBy { it.id } }
            val categories = remember(streams) {
                streams.mapNotNull { it.category }.distinctBy { it.id }
                    .sortedByDescending { category -> streams.count { it.category?.id == category.id } }
                    .take(10)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Account loading/failure is independent from anonymous recommendations.
                item(key = "account_following", span = { GridItemSpan(maxLineSpan) }) {
                    AccountFollowingSection { slug -> onStreamClick(slug, null) }
                }
                if (state.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                state.error?.let { error ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
                if (categories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("POPULAR CATEGORIES", style = MaterialTheme.typography.titleSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(categories, key = { it.id }) { category ->
                                    AssistChip(onClick = {}, label = { Text(category.name) })
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("LIVE NOW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(streams, key = { it.id }) { stream ->
                    StreamCard(
                        stream = stream,
                        isDataSaverActive = isDataSaverActive,
                        onClick = { stream.channel?.slug?.let { onStreamClick(it, null) } },
                        onClickWithSource = { source -> stream.channel?.slug?.let { onStreamClick(it, source) } }
                    )
                }
            }
        }
    }
}
