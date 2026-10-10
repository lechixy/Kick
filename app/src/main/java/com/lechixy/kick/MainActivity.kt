package com.lechixy.kick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.lechixy.kick.data.repository.LocalSettingsRepository
import com.lechixy.kick.data.repository.SettingsRepository
import com.lechixy.kick.ui.components.PlayerHost
import com.lechixy.kick.ui.screens.browse.BrowseScreen
import com.lechixy.kick.ui.screens.channel.ChannelScreen
import com.lechixy.kick.ui.screens.channel.findActivity
import com.lechixy.kick.ui.screens.following.FollowingScreen
import com.lechixy.kick.ui.screens.following.FollowingViewModel
import com.lechixy.kick.ui.screens.home.HomeScreen
import com.lechixy.kick.ui.screens.home.HomeViewModel
import com.lechixy.kick.ui.screens.settings.SettingsScreen
import com.lechixy.kick.ui.screens.settings.general.GeneralScreen
import com.lechixy.kick.ui.theme.KickTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CompositionLocalProvider(
                LocalSettingsRepository provides settingsRepository
            ) {
                KickTheme {
                    KickApp()
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val activeIcon: Int,
    val inactiveIcon: Int = activeIcon
) {
    HOME(
        "Home",
        R.drawable.home_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.home_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    FOLLOWING(
        "Following",
        R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    BROWSE("Browse", R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    SETTINGS("Settings", R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24, R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
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

    // Tek yerde tutulan player oturumu; ChannelScreen, mini player ve nav bar buradan beslenir.
    val playerHost = rememberPlayerHostState()
    var isInPipMode by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        val componentActivity = activity as? ComponentActivity
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { componentActivity?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    val isInsideChannel = currentRoute?.startsWith("channel/") == true

    // Route -> player modu senkronu (örn. alt bardan başka sekmeye geçince kanal sayfası kapanır -> mini olur).
    LaunchedEffect(isInsideChannel) {
        if (!isInsideChannel && playerHost.isChannelVisible) playerHost.minimize()
        if (isInsideChannel && playerHost.mode == PlayerMode.Mini) playerHost.expand()
    }

    val openChannel: (String) -> Unit = { slug ->
        playerHost.open(slug)
        navController.navigate("channel/$slug")
    }

    val isImmersive = playerHost.mode == PlayerMode.Fullscreen || isInPipMode

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        KickNavScaffold(
            hidden = isImmersive,
            currentRoute = currentRoute,
            onNavigate = { targetRoute ->
                if (currentRoute != targetRoute) {
                    navController.navigate(targetRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            }
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                            onStreamClick = { channelSlug -> openChannel(channelSlug) },
                            onSettingsClick = { navController.navigate("settings") }
                        )
                    }

                    composable("following") {
                        val followingViewModel: FollowingViewModel = viewModel()
                        FollowingScreen(
                            viewModel = followingViewModel,
                            onStreamClick = { channelSlug -> openChannel(channelSlug) }
                        )
                    }

                    composable("browse") {
                        BrowseScreen(
                            onChannelClick = { channelSlug -> openChannel(channelSlug) }
                        )
                    }

                    composable(
                        route = "channel/{channelSlug}",
                        arguments = listOf(navArgument("channelSlug") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val slug = backStackEntry.arguments?.getString("channelSlug") ?: ""

                        // Deep link / süreç yeniden başlatma: oturum yoksa bu kanalla başlat.
                        LaunchedEffect(slug) { if (playerHost.slug != slug) playerHost.open(slug) }

                        // Geri: kanal sayfasını kapat, player mini olarak kalsın.
                        BackHandler(enabled = playerHost.mode == PlayerMode.Expanded) {
                            playerHost.minimize()
                            navController.popBackStack()
                        }

                        ChannelScreen(
                            channelSlug = slug,
                            onChannelLoaded = { channelDetail ->
                                playerHost.onChannelLoaded(slug, channelDetail)
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onBackClick = { navController.popBackStack() },
                            onSubsettingClick = { subsettingRoute -> navController.navigate("settings/${subsettingRoute.name.lowercase()}") }
                        )
                    }

                    composable(
                        route = "settings/{settingRoute}",
                        arguments = listOf(navArgument("settingRoute") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val settingRoute = backStackEntry.arguments?.getString("settingRoute") ?: ""

                        when (settingRoute) {
                            "general" -> {
                                GeneralScreen { navController.popBackStack() }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    Arrangement.Center,
                                    Alignment.CenterVertically
                                ) {
                                    Text("Unknown route: $settingRoute")
                                }
                            }
                        }
                    }
                }

                // NavHost'un ÜSTÜNDE: inline / tam ekran / mini / PiP hepsi tek player.
                PlayerHost(
                    host = playerHost,
                    isInPipMode = isInPipMode,
                    onMinimize = {
                        playerHost.minimize()
                        if (isInsideChannel) navController.popBackStack()
                    },
                    onExpand = {
                        val slug = playerHost.slug
                        if (slug != null && !isInsideChannel) {
                            playerHost.expand()
                            navController.navigate("channel/$slug") { launchSingleTop = true }
                        }
                    },
                    onClose = playerHost::close
                )

                // Tam ekranda geri -> sadece tam ekrandan çık (NavHost'tan sonra tanımlı = öncelikli).
                BackHandler(enabled = playerHost.mode == PlayerMode.Fullscreen) {
                    playerHost.setFullscreen(false)
                }
            }
        }
    }
}

/**
 * NavigationSuiteScaffold yerine: bar/rail ANİMASYONLU gizlenir (kayarak + boyut küçülerek),
 * içerik her karede kalan alanın tamamını doldurur -> ani boşalma / sıçrama yok.
 * Bar tipi (alt bar / rail) eskisiyle aynı adaptive hesaptan gelir.
 */
@Composable
private fun KickNavScaffold(
    hidden: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val type =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    // Varsayım: adı "NavigationBar" içeren tipler alt bar, diğerleri (rail/drawer) yan rail.
    val useBottomBar = type.toString().contains("NavigationBar", ignoreCase = true)

    @Composable
    fun Items(rail: Boolean) {
        AppDestinations.entries.forEach { destination ->
            val targetRoute = destination.name.lowercase()
            val isSelected = currentRoute == targetRoute
            val icon: @Composable () -> Unit = {
                Icon(
                    painter = painterResource(id = if (isSelected) destination.activeIcon else destination.inactiveIcon),
                    contentDescription = destination.label
                )
            }
            val label: @Composable () -> Unit = { Text(destination.label) }
            if (rail) {
                NavigationRailItem(
                    selected = isSelected, onClick = { onNavigate(targetRoute) },
                    icon = icon, label = label
                )
            } else {
//                NavigationBarItem(
//                    selected = isSelected, onClick = { onNavigate(targetRoute) },
//                    icon = icon, label = label
//                )
            }
        }
    }

    if (useBottomBar) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .let {
                        if (hidden) it else it.consumeWindowInsets(
                            WindowInsets.systemBars.only(
                                WindowInsetsSides.Bottom
                            )
                        )
                    }
            ) { content() }
            AnimatedVisibility(
                visible = !hidden,
                enter = slideInVertically { it } + expandVertically(),
                exit = slideOutVertically { it } + shrinkVertically()
            ) {
                NavigationBar { Items(rail = false) }
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !hidden,
                enter = slideInHorizontally { -it } + expandHorizontally(),
                exit = slideOutHorizontally { -it } + shrinkHorizontally()
            ) {
                NavigationRail { Items(rail = true) }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .let {
                        if (hidden) it else it.consumeWindowInsets(
                            WindowInsets.systemBars.only(
                                WindowInsetsSides.Start
                            )
                        )
                    }
            ) { content() }
        }
    }
}