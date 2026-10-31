package com.lechixy.kick.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lechixy.kick.R
import com.lechixy.kick.ui.components.ExpansionSource
import com.lechixy.kick.ui.components.HomeTopBar
import com.lechixy.kick.ui.components.LiveTotalsButton
import com.lechixy.kick.ui.components.SkeletonLoadingScope
import com.lechixy.kick.ui.components.StreamCard
import com.lechixy.kick.ui.components.StreamCardSkeleton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onStreamClick: (channelSlug: String, source: ExpansionSource?) -> Unit,
    onSettingsClick: () -> Unit,
    onFollowingClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val pullState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var totalsVisible by remember { mutableStateOf(true) }
    val atTop by remember { derivedStateOf { !gridState.canScrollBackward } }
    val scrollThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val totalsScrollConnection = remember(scrollThreshold) {
        object : NestedScrollConnection {
            private var distance = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || consumed.y == 0f) return Offset.Zero
                if (distance * consumed.y < 0f) distance = 0f
                distance += consumed.y
                if (distance <= -scrollThreshold) totalsVisible = false
                if (distance >= scrollThreshold) totalsVisible = true
                return Offset.Zero
            }
        }
    }

    val isDataSaverActive = false

    SkeletonLoadingScope(active = (state.isLoading && state.streams.isEmpty()) ||
        (state.following.signedIn && state.following.loading && state.following.streams.isEmpty())) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    onSearchClick = {},
                    onSettingsClick = onSettingsClick,
                    onChannelClick = { onStreamClick(it, null) }
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
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize().nestedScroll(totalsScrollConnection),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp,
                        bottom = if (state.liveTotals != null) 100.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.following.signedIn) {
                        item(key = "following_header", span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.home_following_live), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = onFollowingClick) { Text(stringResource(R.string.home_more)) }
                                }
                                state.following.error?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = viewModel::loadFollowing) { Text(stringResource(R.string.chat_retry)) }
                                }
                                if (state.following.loaded && state.following.streams.isEmpty()) {
                                    Text(stringResource(R.string.home_no_following_live), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (state.following.loading && state.following.streams.isEmpty()) {
                            items(2, key = { "following_skeleton_$it" }) { StreamCardSkeleton() }
                        }
                        items(state.following.streams, key = { "following_${it.id}" }) { stream ->
                            StreamCard(
                                stream = stream,
                                isDataSaverActive = isDataSaverActive,
                                onClick = { stream.channel?.slug?.let { onStreamClick(it, null) } },
                                onClickWithSource = { source -> stream.channel?.slug?.let { onStreamClick(it, source) } }
                            )
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
                                Text(stringResource(R.string.home_popular_categories), style = MaterialTheme.typography.titleSmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(categories, key = { it.id }) { category ->
                                        AssistChip(onClick = {}, label = { Text(category.name) })
                                    }
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.home_recommended_live), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (state.isLoading && streams.isEmpty()) {
                        items(6, key = { "recommended_skeleton_$it" }) { StreamCardSkeleton() }
                    }
                    items(streams, key = { "recommended_${it.id}" }) { stream ->
                        StreamCard(
                            stream = stream,
                            isDataSaverActive = isDataSaverActive,
                            onClick = { stream.channel?.slug?.let { onStreamClick(it, null) } },
                            onClickWithSource = { source -> stream.channel?.slug?.let { onStreamClick(it, source) } }
                        )
                    }
                }
                AnimatedVisibility(
                    visible = state.liveTotals != null && (totalsVisible || atTop),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 12.dp),
                    enter = scaleIn(
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + fadeIn(),
                    exit = scaleOut(
                        animationSpec = spring(dampingRatio = 1f, stiffness = 650f),
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    ) + fadeOut()
                ) {
                    state.liveTotals?.let { totals ->
                        LiveTotalsButton(totals = totals, onClick = {
                            totalsVisible = true
                            scope.launch { gridState.animateScrollToItem(0) }
                        })
                    }
                }
            }
        }
    }
}
