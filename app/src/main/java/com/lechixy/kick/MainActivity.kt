package com.lechixy.kick

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.lechixy.kick.data.local.FollowManager
import com.lechixy.kick.data.local.PlayerSettingsManager
import com.lechixy.kick.ui.screens.browse.BrowseScreen
import com.lechixy.kick.ui.screens.channel.ChannelScreen
import com.lechixy.kick.ui.screens.following.FollowingScreen
import com.lechixy.kick.ui.screens.following.FollowingViewModel
import com.lechixy.kick.ui.screens.home.HomeScreen
import com.lechixy.kick.ui.screens.home.HomeViewModel
import com.lechixy.kick.ui.screens.settings.SettingsScreen
import com.lechixy.kick.ui.theme.KickTheme
import com.lechixy.kick.util.buildOptimizedImageRequest

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
    val icon: Int
) {
    HOME("Home", R.drawable.home_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
    FOLLOWING("Following", R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    BROWSE("Browse", R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
}

@Composable
fun KickApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    FollowManager.init(context);
    PlayerSettingsManager.init(context);

    var isInPipMode by remember { mutableStateOf(false) }

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

    val isInsideChannel = currentRoute?.startsWith("channel/") == true

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            // PiP modundayken menü tamamen boşaltılır
            if (!isInPipMode) {
                AppDestinations.entries.forEach { destination ->
                    val targetRoute = destination.name.lowercase()
                    val isSelected = currentRoute == targetRoute

                    item(
                        icon = {
                            Icon(
                                painter = painterResource(id = destination.icon),
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

                if (isInsideChannel && ActiveChannelState.activeSlug != null) {
                    item(
                        icon = {
                            if (!ActiveChannelState.activeProfilePic.isNullOrBlank()) {
                                AsyncImage(
                                    model = remember(ActiveChannelState.activeProfilePic) {
                                        buildOptimizedImageRequest(
                                            context = context,
                                            data = ActiveChannelState.activeProfilePic,
                                            targetWidthDp = 24,
                                            targetHeightDp = 24
                                        )
                                    },
                                    contentDescription = ActiveChannelState.activeUsername,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    contentDescription = "Active Channel"
                                )
                            }
                        },
                        label = {
                            Text(
                                text = ActiveChannelState.activeUsername ?: ActiveChannelState.activeSlug ?: "Channel",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = true,
                        onClick = {}
                    )
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onStreamClick = { channelSlug ->
                        navController.navigate("channel/$channelSlug")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("following") {
                val followingViewModel: FollowingViewModel = viewModel()
                FollowingScreen(
                    viewModel = followingViewModel,
                    onStreamClick = { channelSlug ->
                        navController.navigate("channel/$channelSlug")
                    }
                )
            }

            composable("browse") {
                BrowseScreen(
                    onChannelClick = { channelSlug ->
                        navController.navigate("channel/$channelSlug")
                    }
                )
            }

            composable(
                route = "channel/{channelSlug}",
                arguments = listOf(navArgument("channelSlug") { type = NavType.StringType })
            ) { backStackEntry ->
                val slug = backStackEntry.arguments?.getString("channelSlug") ?: ""

                DisposableEffect(slug) {
                    ActiveChannelState.activeSlug = slug
                    onDispose {
                        ActiveChannelState.activeSlug = null
                        ActiveChannelState.activeUsername = null
                        ActiveChannelState.activeProfilePic = null
                    }
                }

                ChannelScreen(
                    channelSlug = slug,
                    onBackClick = { navController.popBackStack() },
                    onChannelLoaded = { channelDetail ->
                        ActiveChannelState.activeUsername = channelDetail.user?.username
                        ActiveChannelState.activeProfilePic = channelDetail.user?.profilePic
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}