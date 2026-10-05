package com.lechixy.kick.ui.screens.channel

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.local.FollowManager
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.data.model.ChannelVideo
import com.lechixy.kick.ui.components.KickPlayerView
import com.lechixy.kick.ui.components.VerifiedBadge
import com.lechixy.kick.ui.components.VideoCard
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest

enum class ChannelTab(val title: String) {
    HOME("Home"),
    ABOUT("About"),
    VIDEOS("Videos"),
    CLIPS("Clips")
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelSlug: String,
    viewModel: ChannelViewModel = viewModel(),
    onBackClick: () -> Unit,
    onChannelLoaded: (ChannelDetail) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    var selectedTab by remember { mutableStateOf(ChannelTab.HOME) }
    var isInPipMode by remember { mutableStateOf(false) }

    // PiP modunu dinleme
    DisposableEffect(activity) {
        val listener = object : androidx.core.util.Consumer<PictureInPictureModeChangedInfo> {
            override fun accept(value: PictureInPictureModeChangedInfo) {
                isInPipMode = value.isInPictureInPictureMode
            }
        }
        if (activity is ComponentActivity) {
            activity.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if (activity is ComponentActivity) {
                activity.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }

    val followedSlugs by FollowManager.followedSlugs.collectAsState()
    val isFollowing = remember(followedSlugs, channelSlug) {
        FollowManager.isFollowing(channelSlug)
    }

    LaunchedEffect(channelSlug) {
        viewModel.loadChannel(channelSlug)
    }

    LaunchedEffect(state.channel) {
        state.channel?.let { onChannelLoaded(it) }
    }

    // Normal mod: Standart Scaffold ve Sayfa Düzeni
    Scaffold(
        topBar = {
            // PiP modundayken TopAppBar çizilmez
            if (!isInPipMode) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = state.channel?.user?.username ?: state.channel?.slug
                                ?: channelSlug,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.channel?.verified == true) {
                                VerifiedBadge(
                                    modifier = Modifier.size(16.dp),
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        // PiP modundaysa padding olmadan sadece tam ekran Player gösterilir
        if (isInPipMode) {
            state.channel?.let { channel ->
                VideoPlayerBox(
                    isLive = channel.livestream != null,
                    channel = channel,
                    context = context,
                    activity = activity
                )
            }
        } else {
            // Normal mod (when block: isLoading, error, channel content...)
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
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                state.channel != null -> {
                    val channel = state.channel!!
                    val isLive = channel.livestream != null
                    val lastLiveText = remember(state.videos) {
                        val latestVideo = state.videos.firstOrNull()
                        FormatUtils.formatLastLive(latestVideo?.startTime ?: latestVideo?.createdAt)
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        val isWideScreen = maxWidth >= 700.dp

                        if (isWideScreen) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .fillMaxHeight()
                                ) {
                                    VideoPlayerBox(
                                        isLive = isLive,
                                        channel = channel,
                                        context = context,
                                        activity = activity
                                    )

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        item {
                                            ChannelHeaderInfo(
                                                channel = channel,
                                                isLive = isLive,
                                                lastLiveText = lastLiveText,
                                                isFollowing = isFollowing,
                                                onFollowClick = {
                                                    FollowManager.toggleFollow(
                                                        channelSlug
                                                    )
                                                },
                                                context = context
                                            )
                                        }
                                        item {
                                            ChannelTabsRow(
                                                selectedTab = selectedTab,
                                                onTabSelected = { selectedTab = it }
                                            )
                                        }
                                        item {
                                            ChannelTabContent(
                                                tab = selectedTab,
                                                channel = channel,
                                                videos = state.videos,
                                                onNavigateToVideos = {
                                                    selectedTab = ChannelTab.VIDEOS
                                                }
                                            )
                                        }
                                    }
                                }

                                VerticalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(0.7f)
                                        .fillMaxHeight()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.2f
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = "STREAM CHAT",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Chatroom ID: ${channel.chatroom?.id ?: "N/A"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                VideoPlayerBox(
                                    isLive = isLive,
                                    channel = channel,
                                    context = context,
                                    activity = activity
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    item {
                                        ChannelHeaderInfo(
                                            channel = channel,
                                            isLive = isLive,
                                            lastLiveText = lastLiveText,
                                            isFollowing = isFollowing,
                                            onFollowClick = { FollowManager.toggleFollow(channelSlug) },
                                            context = context
                                        )
                                    }
                                    item {
                                        ChannelTabsRow(
                                            selectedTab = selectedTab,
                                            onTabSelected = { selectedTab = it }
                                        )
                                    }
                                    item {
                                        ChannelTabContent(
                                            tab = selectedTab,
                                            channel = channel,
                                            videos = state.videos,
                                            onNavigateToVideos = { selectedTab = ChannelTab.VIDEOS }
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

@Composable
private fun ChannelHeaderInfo(
    channel: ChannelDetail,
    isLive: Boolean,
    lastLiveText: String,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = remember(channel.user?.profilePic) {
                buildOptimizedImageRequest(
                    context = context,
                    data = channel.user?.profilePic,
                    targetWidthDp = 64,
                    targetHeightDp = 64
                )
            },
            contentDescription = channel.user?.username,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = channel.user?.username ?: channel.slug,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (channel.verified) {
                    VerifiedBadge(
                        modifier = Modifier.size(16.dp),
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = FormatUtils.formatFollowersCount(channel.followersCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "followers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Canlı yayın yoksa Last live gösterimi (onSurface rengi ile açık/koyu temaya uyumlu)
            if (!isLive) {
                Text(
                    text = lastLiveText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Takip Butonu
        Button(
            onClick = onFollowClick,
            shape = RoundedCornerShape(8.dp),
            colors = if (isFollowing) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChannelTabsRow(
    selectedTab: ChannelTab,
    onTabSelected: (ChannelTab) -> Unit
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        ChannelTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.title,
                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelTabContent(
    tab: ChannelTab,
    channel: ChannelDetail,
    videos: List<ChannelVideo>,
    onNavigateToVideos: () -> Unit
) {
    when (tab) {
        ChannelTab.HOME -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (videos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Broadcasts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateToVideos) {
                            Text(
                                text = "Daha Fazla",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // En fazla 4 video göster
                    val recentList = videos.take(4)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 3
                    ) {
                        recentList.forEach { video ->
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .widthIn(min = 280.dp)
                            ) {
                                VideoCard(video = video)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Henüz içerik bulunmuyor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        ChannelTab.ABOUT -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (!channel.user?.bio.isNullOrBlank()) channel.user?.bio!! else "Biyografi eklenmemiş.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        ChannelTab.VIDEOS -> {
            if (videos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kayıtlı video bulunamadı.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 3
                ) {
                    videos.forEach { video ->
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(min = 280.dp)
                        ) {
                            VideoCard(video = video)
                        }
                    }
                }
            }
        }

        ChannelTab.CLIPS -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Klipler yakında eklenecek.",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerBox(
    isLive: Boolean,
    channel: ChannelDetail,
    context: Context,
    activity: Activity?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val playbackUrl = channel.playbackUrl

        if (isLive && !playbackUrl.isNullOrBlank()) {
            KickPlayerView(
                playbackUrl = playbackUrl,
                channelName = channel.user?.username ?: channel.slug,
                streamTitle = channel.livestream?.sessionTitle ?: "Canlı Yayın",
                channel = channel,
                onPlayerCoordinatesChanged = { rect ->
                    // Android 12+ Akıcı PiP: Küçülme animasyonunun player üzerinden başlamasını sağlar
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .setSourceRectHint(rect)
                            .setAutoEnterEnabled(true)
                            .build()
                        activity.setPictureInPictureParams(params)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bannerUrl = channel.offlineBannerImage?.src ?: channel.bannerImage?.url
            if (!bannerUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = remember(bannerUrl) {
                        buildOptimizedImageRequest(
                            context = context,
                            data = bannerUrl,
                            targetWidthDp = 640,
                            targetHeightDp = 360
                        )
                    },
                    contentDescription = "Offline Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "CHANNEL OFFLINE",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}