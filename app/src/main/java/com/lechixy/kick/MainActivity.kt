package com.lechixy.kick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lechixy.kick.data.local.FollowManager
import com.lechixy.kick.data.local.PlayerSettingsManager
import com.lechixy.kick.ui.screens.browse.BrowseScreen
import com.lechixy.kick.ui.screens.channel.ChannelScreen
import com.lechixy.kick.ui.screens.channel.findActivity
import com.lechixy.kick.ui.screens.following.FollowingScreen
import com.lechixy.kick.ui.screens.following.FollowingViewModel
import com.lechixy.kick.ui.screens.home.HomeScreen
import com.lechixy.kick.ui.screens.home.HomeViewModel
import com.lechixy.kick.ui.screens.settings.SettingsScreen
import com.lechixy.kick.ui.theme.KickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KickTheme {
                KickApp()
            }
        }
    }
}

// Dinamik aktif kanal durumunu tutan yardımcı holder
object ActiveChannelState {
    var activeSlug by mutableStateOf<String?>(null)
    var activeUsername by mutableStateOf<String?>(null)
    var activeProfilePic by mutableStateOf<String?>(null)
}

enum class AppDestinations(
    val label: String,
    val activeIcon: Int,
    val inactiveIcon: Int = activeIcon
) {
    HOME("Home", R.drawable.home_24dp_e3e3e3_fill1_wght400_grad0_opsz24, R.drawable.home_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    FOLLOWING("Following", R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24, R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    BROWSE("Browse", R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
}

@Composable
fun KickApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    FollowManager.init(context)
    PlayerSettingsManager.init(context)

    // Tek yerde tutulan "immersive" state'leri; ChannelScreen ve nav bar buradan beslenir.
    var isInPipMode by remember { mutableStateOf(false) }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        val componentActivity = activity as? ComponentActivity
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { componentActivity?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    val isInsideChannel = currentRoute?.startsWith("channel/") == true

    // Kanal ekranından çıkınca fullscreen kalmasın
    LaunchedEffect(isInsideChannel) {
        if (!isInsideChannel) isFullScreen = false
    }

    val isImmersive = isFullScreen || isInPipMode
    val navLayoutType = if (isImmersive) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    }

    NavigationSuiteScaffold(
        layoutType = navLayoutType,
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                val targetRoute = destination.name.lowercase()
                val isSelected = currentRoute == targetRoute

                item(
                    icon = {
                        Icon(
                            painter = painterResource(id = if (isSelected) destination.activeIcon else destination.inactiveIcon),
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = isSelected,
                    onClick = {
                        if (currentRoute != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

//            if (isInsideChannel && ActiveChannelState.activeSlug != null) {
//                item(
//                    icon = {
//                        if (!ActiveChannelState.activeProfilePic.isNullOrBlank()) {
//                            AsyncImage(
//                                model = remember(ActiveChannelState.activeProfilePic) {
//                                    buildOptimizedImageRequest(
//                                        context = context,
//                                        data = ActiveChannelState.activeProfilePic,
//                                        targetWidthDp = 24,
//                                        targetHeightDp = 24
//                                    )
//                                },
//                                contentDescription = ActiveChannelState.activeUsername,
//                                modifier = Modifier
//                                    .size(24.dp)
//                                    .clip(CircleShape),
//                                contentScale = ContentScale.Crop
//                            )
//                        } else {
//                            Icon(
//                                painter = painterResource(id = R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
//                                contentDescription = "Active Channel"
//                            )
//                        }
//                    },
//                    label = {
//                        Text(
//                            text = ActiveChannelState.activeUsername
//                                ?: ActiveChannelState.activeSlug
//                                ?: "Channel",
//                            maxLines = 1,
//                            overflow = TextOverflow.Ellipsis
//                        )
//                    },
//                    selected = true,
//                    onClick = {}
//                )
//            }
        }
    ) {
        val animDuration = 350
        NavHost(
            navController = navController,
            startDestination = "home",
            // 1. İLERİ GİDİŞ: Hedef ekran içeri girerken
            enterTransition = {
                fadeIn(animationSpec = tween(animDuration, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.94f,
                            animationSpec = tween(animDuration, easing = FastOutSlowInEasing)
                        )
            },

            // 2. İLERİ GİDİŞ: Arkada kalan (Home vb.) ekrandan çıkarken
            exitTransition = {
                fadeOut(animationSpec = tween(animDuration, easing = FastOutSlowInEasing)) +
                        scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(animDuration, easing = FastOutSlowInEasing)
                        )
            },

            // 3. GERİ GELİŞ: Geri dönülen sayfa (Home) tekrar görünürken
            popEnterTransition = {
                fadeIn(animationSpec = tween(animDuration, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(animDuration, easing = FastOutSlowInEasing)
                        )
            },

            // 4. GERİ GELİŞ: Kapatılan sayfa yok olurken
            popExitTransition = {
                fadeOut(animationSpec = tween(animDuration, easing = FastOutSlowInEasing)) +
                        scaleOut(
                            targetScale = 0.94f,
                            animationSpec = tween(animDuration, easing = FastOutSlowInEasing)
                        )
            }
        ) {
            composable("home") {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onStreamClick = { channelSlug -> navController.navigate("channel/$channelSlug") },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }

            composable("following") {
                val followingViewModel: FollowingViewModel = viewModel()
                FollowingScreen(
                    viewModel = followingViewModel,
                    onStreamClick = { channelSlug -> navController.navigate("channel/$channelSlug") }
                )
            }

            composable("browse") {
                BrowseScreen(
                    onChannelClick = { channelSlug -> navController.navigate("channel/$channelSlug") }
                )
            }

            composable(
                route = "channel/{channelSlug}",
                arguments = listOf(navArgument("channelSlug") { type = NavType.StringType })
            ) { backStackEntry ->
                val slug = backStackEntry.arguments?.getString("channelSlug") ?: ""
//
//                DisposableEffect(slug) {
//                    ActiveChannelState.activeSlug = slug
//                    onDispose {
//                        ActiveChannelState.activeSlug = null
//                        ActiveChannelState.activeUsername = null
//                        ActiveChannelState.activeProfilePic = null
//                    }
//                }

                ChannelScreen(
                    channelSlug = slug,
                    isFullScreen = isFullScreen,
                    isInPipMode = isInPipMode,
                    onFullScreenChange = { isFullScreen = it },
                    onBackClick = { navController.popBackStack() },
                    onChannelLoaded = { channelDetail ->
//                        ActiveChannelState.activeUsername = channelDetail.user?.username
//                        ActiveChannelState.activeProfilePic = channelDetail.user?.profilePic
                    }
                )
            }

            composable("settings") {
                SettingsScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}