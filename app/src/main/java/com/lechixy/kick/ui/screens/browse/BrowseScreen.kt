package com.lechixy.kick.ui.screens.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.model.toChannelDetail
import com.lechixy.kick.ui.components.ChannelCard
import com.lechixy.kick.util.buildOptimizedImageRequest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel = viewModel(),
    onChannelClick: (channelSlug: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Search for channels, categories or streams...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
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

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "Search error",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            state.searchData != null -> {
                val data = state.searchData!!
                val hasChannels = data.channels.isNotEmpty()
                val hasCategories = data.categories.isNotEmpty()
                val hasStreams = data.livestreams.isNotEmpty()

                if (!hasChannels && !hasCategories && !hasStreams) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sonuç bulunamadı",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // 1. CHANNELS (Responsive Grid / Flow)
                        if (hasChannels) {
                            item {
                                Text(
                                    text = "CHANNELS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = 3
                                ) {
                                    data.channels.forEach { ch ->
                                        ChannelCard(
                                            channel = ch.toChannelDetail(),
                                            onChannelClick = onChannelClick
                                        )
                                    }
                                }
                            }
                        }

                        // 2. CATEGORIES
                        if (hasCategories) {
                            item {
                                Text(
                                    text = "CATEGORIES",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(data.categories, key = { "cat_${it.slug}" }) { cat ->
                                        Card(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .clickable { /* İleride category filter */ },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.3f
                                                )
                                            )
                                        ) {
                                            Column {
                                                AsyncImage(
                                                    model = remember(cat.thumbnail?.src) {
                                                        buildOptimizedImageRequest(
                                                            context = context,
                                                            data = cat.thumbnail?.src,
                                                            targetWidthDp = 130,
                                                            targetHeightDp = 170
                                                        )
                                                    },
                                                    contentDescription = cat.name,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(160.dp),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Text(
                                                    text = cat.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. LIVESTREAMS (Responsive Grid / Flow)
                        if (hasStreams) {
                            item {
                                Text(
                                    text = "LIVESTREAMS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    maxItemsInEachRow = 3
                                ) {
                                    data.livestreams.forEach { stream ->
                                        Card(
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .widthIn(min = 280.dp)
                                                .clickable { onChannelClick(stream.slug) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.3f
                                                )
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .aspectRatio(16f / 9f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                ) {
                                                    AsyncImage(
                                                        model = remember(stream.thumbnail?.src) {
                                                            buildOptimizedImageRequest(
                                                                context = context,
                                                                data = stream.thumbnail?.src,
                                                                targetWidthDp = 120,
                                                                targetHeightDp = 68
                                                            )
                                                        },
                                                        contentDescription = stream.title,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }

                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = stream.title ?: "No Title",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = stream.slug,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aramak istediğin kanalı veya içeriği yaz",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}