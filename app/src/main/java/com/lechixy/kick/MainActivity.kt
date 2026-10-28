package com.lechixy.kick

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lechixy.kick.data.settings.LocalSettingsRepository
import com.lechixy.kick.data.settings.SettingsKeys
import com.lechixy.kick.data.settings.SettingsRepository
import com.lechixy.kick.ui.components.ExpansionSource
import com.lechixy.kick.ui.components.PlayerHost
import com.lechixy.kick.ui.screens.browse.BrowseScreen
import com.lechixy.kick.ui.screens.channel.ChannelScreen
import com.lechixy.kick.ui.screens.channel.ChannelViewModel
import com.lechixy.kick.ui.screens.channel.findActivity
import com.lechixy.kick.ui.screens.following.FollowingScreen
import com.lechixy.kick.ui.screens.following.FollowingViewModel
import com.lechixy.kick.ui.screens.home.HomeScreen
import com.lechixy.kick.ui.screens.home.HomeViewModel
import com.lechixy.kick.ui.screens.settings.SettingsScreen
import com.lechixy.kick.ui.screens.settings.general.GeneralSettingsScreen
import com.lechixy.kick.ui.theme.KickTheme
import com.lechixy.kick.ui.theme.Theme
import com.lechixy.kick.util.LanguageUtils.getCurrentAppLanguage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onResume() {
        super.onResume()
        syncLanguageWithDataStore()
    }

    private fun syncLanguageWithDataStore() {
        val systemLang = getCurrentAppLanguage(this)

        lifecycleScope.launch {
            val savedLang = settingsRepository.get(SettingsKeys.APP_LANGUAGE).first()
            // Sistemdeki dil ile DataStore farklıysa DataStore'u güncelle
            if (systemLang.isNotEmpty() && systemLang != savedLang) {
                settingsRepository.set(SettingsKeys.APP_LANGUAGE, systemLang)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val currentSystemLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()

            settingsRepository.get(SettingsKeys.APP_LANGUAGE).first().let { savedLang ->
                if (currentSystemLocale.isNotEmpty() && currentSystemLocale != savedLang) {
                    settingsRepository.set(SettingsKeys.APP_LANGUAGE, currentSystemLocale)
                } else if (currentSystemLocale.isEmpty() && savedLang.isNotEmpty()) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(
                            savedLang
                        )
                    )
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalSettingsRepository provides settingsRepository
            ) {
                val repository = LocalSettingsRepository.current
                val theme by repository
                    .get(SettingsKeys.THEME)
                    .collectAsStateWithLifecycle(initialValue = SettingsKeys.THEME.defaultValue)
                val dynamicColor by repository
                    .get(SettingsKeys.DYNAMIC_COLOR)
                    .collectAsStateWithLifecycle(initialValue = SettingsKeys.DYNAMIC_COLOR.defaultValue)

                val themeEnum = when (theme) {
                    "system" -> Theme.SYSTEM
                    "light" -> Theme.LIGHT
                    "dark" -> Theme.DARK
                    "amoled" -> Theme.AMOLED
                    else -> Theme.SYSTEM
                }

                KickTheme(
                    theme = themeEnum,
                    dynamicColor = dynamicColor
                ) {
                    KickApp()
                }
            }
        }
    }
}

enum class AppDestinations(
    @StringRes val label: Int,
    val activeIcon: Int,
    val inactiveIcon: Int = activeIcon
) {
    HOME(
        R.string.home,
        R.drawable.home_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.home_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    FOLLOWING(
        R.string.following,
        R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    BROWSE(R.string.browse, R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    EVENTS(
        R.string.events,
        R.drawable.local_activity_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.local_activity_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    SETTINGS(
        R.string.settings,
        R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    );

    fun getLabel(context: Context): String {
        return context.getString(this.label)
    }
}

@Composable
fun KickApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }


    // Tek yerde tutulan player oturumu; ChannelScreen, mini player ve nav bar buradan beslenir.
    val playerHost = rememberPlayerHostState()
    val settingsRepository = LocalSettingsRepository.current
    LaunchedEffect(settingsRepository) {
        settingsRepository.get(SettingsKeys.CHAT_WIDTH_FRACTION).collect { savedFraction ->
            playerHost.updateChatWidthFraction(savedFraction)
        }
    }
    var isInPipMode by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        val componentActivity = activity as? ComponentActivity
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { componentActivity?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    val openChannel: (String, ExpansionSource?) -> Unit =
        { slug, source -> playerHost.open(slug, source) }
    val navType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    val useBottomBar = navType.toString().contains("NavigationBar", ignoreCase = true)
    val isScaffoldHidden = if (useBottomBar) {
        playerHost.isChannelVisible || playerHost.mode == PlayerMode.Fullscreen || isInPipMode
    } else {
        (playerHost.mode == PlayerMode.Fullscreen && !playerHost.isFullscreenNavigationVisible) ||
                (playerHost.mode == PlayerMode.Expanded && playerHost.isTheaterMode) || isInPipMode
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.fillMaxSize()) {
            KickNavScaffold(
                hidden = isScaffoldHidden,
                currentRoute = currentRoute,
                onNavigate = { targetRoute ->
                    if (playerHost.isChannelVisible) playerHost.minimize()
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val animDuration = 350
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        // 1. İLERİ GİDİŞ: Hedef ekran içeri girerken
                        enterTransition = {
                            fadeIn(
                                animationSpec = tween(
                                    animDuration,
                                    easing = FastOutSlowInEasing
                                )
                            ) +
                                    scaleIn(
                                        initialScale = 0.94f,
                                        animationSpec = tween(
                                            animDuration,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                        },

                        // 2. İLERİ GİDİŞ: Arkada kalan (Home vb.) ekrandan çıkarken
                        exitTransition = {
                            fadeOut(
                                animationSpec = tween(
                                    animDuration,
                                    easing = FastOutSlowInEasing
                                )
                            ) +
                                    scaleOut(
                                        targetScale = 0.98f,
                                        animationSpec = tween(
                                            animDuration,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                        },

                        // 3. GERİ GELİŞ: Geri dönülen sayfa (Home) tekrar görünürken
                        popEnterTransition = {
                            fadeIn(
                                animationSpec = tween(
                                    animDuration,
                                    easing = FastOutSlowInEasing
                                )
                            ) +
                                    scaleIn(
                                        initialScale = 0.98f,
                                        animationSpec = tween(
                                            animDuration,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                        },

                        // 4. GERİ GELİŞ: Kapatılan sayfa yok olurken
                        popExitTransition = {
                            fadeOut(
                                animationSpec = tween(
                                    animDuration,
                                    easing = FastOutSlowInEasing
                                )
                            ) +
                                    scaleOut(
                                        targetScale = 0.94f,
                                        animationSpec = tween(
                                            animDuration,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                        }
                    ) {
                        composable("home") {
                            val homeViewModel: HomeViewModel = hiltViewModel()
                            HomeScreen(
                                viewModel = homeViewModel,
                                onStreamClick = { channelSlug, source ->
                                    openChannel(
                                        channelSlug,
                                        source
                                    )
                                },
                                onSettingsClick = { navController.navigate("settings") },
                                onFollowingClick = {
                                    navController.navigate("following") {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable("following") {
                            val followingViewModel: FollowingViewModel = viewModel()
                            FollowingScreen(
                                viewModel = followingViewModel,
                                onStreamClick = { channelSlug, source ->
                                    openChannel(
                                        channelSlug,
                                        source
                                    )
                                }
                            )
                        }

                        composable("browse") {
                            BrowseScreen(
                                onChannelClick = { channelSlug, source ->
                                    openChannel(
                                        channelSlug,
                                        source
                                    )
                                }
                            )
                        }

                        composable("events") {
                            EventsScreen()
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onSubsettingClick = { subsettingRoute -> navController.navigate("settings/${subsettingRoute.name.lowercase()}") }
                            )
                        }

                        composable(
                            route = "settings/{settingRoute}",
                            arguments = listOf(navArgument("settingRoute") {
                                type = NavType.StringType
                            })
                        ) { backStackEntry ->
                            val settingRoute =
                                backStackEntry.arguments?.getString("settingRoute") ?: ""

                            when (settingRoute) {
                                "developer" -> com.lechixy.kick.ui.screens.settings.DeveloperToolsScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onTriggerLive = { playerHost.triggerLiveAnnouncement(System.nanoTime()) },
                                    canTriggerLive = playerHost.channel?.livestream != null && playerHost.recording == null && playerHost.mode != PlayerMode.Hidden
                                )

                                "general" -> {
                                    GeneralSettingsScreen(
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }

                                "playback" -> {
                                    com.lechixy.kick.ui.screens.settings.playback.PlayerPlaybackSettingsScreen(
                                        onBackClick = { navController.popBackStack() }
                                    )
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

                    val activeSlug = playerHost.slug
                    if (activeSlug != null) {
                        val activeChannelVm: ChannelViewModel = viewModel(key = activeSlug)
                        val activeChannelState by activeChannelVm.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(activeSlug, activeChannelState.channel) {
                            activeChannelState.channel?.let { detail ->
                                playerHost.onChannelLoaded(activeSlug, detail)
                            }
                        }
                        LaunchedEffect(activeSlug, activeChannelState.liveAnnouncementId) {
                            val announcementId =
                                activeChannelState.liveAnnouncementId ?: return@LaunchedEffect
                            playerHost.triggerLiveAnnouncement(announcementId)
                            activeChannelVm.consumeLiveAnnouncement(announcementId)
                        }
                    }

                    PlayerHost(
                        host = playerHost,
                        isInPipMode = isInPipMode,
                        onMinimize = playerHost::minimize,
                        onExpand = playerHost::expand,
                        onClose = playerHost::close,
                        pageContent = { slug ->
                            ChannelScreen(
                                channelSlug = slug,
                                playerHost = playerHost,
                                viewModel = viewModel(key = slug),
                                onChannelLoaded = { playerHost.onChannelLoaded(slug, it) },
                                onBackClick = playerHost::minimize,
                            )
                        }
                    )

                    BackHandler(enabled = playerHost.mode == PlayerMode.Expanded && !playerHost.isExiting) { playerHost.minimize() }
                }
            }
            if (!isInPipMode) com.lechixy.kick.ui.components.NetworkInfoBar(Modifier.align(Alignment.BottomCenter))
            com.lechixy.kick.ui.screens.settings.DeveloperToolsOverlay(
                isInPipMode = isInPipMode,
                onTriggerLive = { playerHost.triggerLiveAnnouncement(System.nanoTime()) },
                canTriggerLive = playerHost.channel?.livestream != null && playerHost.recording == null && playerHost.mode != PlayerMode.Hidden
            )
        }
    }
}

@Composable
internal fun KickNavScaffold(
    hidden: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val type =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    val useBottomBar = type.toString().contains("NavigationBar", ignoreCase = true)
    val latestContent by rememberUpdatedState(content)
    val sharedContent = remember { movableContentOf { latestContent() } }

    @Composable
    fun RowScope.BottomBarItems() {
        AppDestinations.entries.forEach { destination ->
            val targetRoute = destination.name.lowercase()
            val isSelected = currentRoute == targetRoute
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(targetRoute) },
                icon = {
                    Icon(
                        painter = painterResource(id = if (isSelected) destination.activeIcon else destination.inactiveIcon),
                        contentDescription = destination.getLabel(context)
                    )
                },
                label = { Text(destination.getLabel(context)) }
            )
        }
    }

    @Composable
    fun RailBarItems() {
        AppDestinations.entries.forEach { destination ->
            val targetRoute = destination.name.lowercase()
            val isSelected = currentRoute == targetRoute
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigate(targetRoute) },
                icon = {
                    Icon(
                        painter = painterResource(id = if (isSelected) destination.activeIcon else destination.inactiveIcon),
                        contentDescription = destination.getLabel(context)
                    )
                },
                label = { Text(destination.getLabel(context)) }
            )
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
            ) { sharedContent() }
            AnimatedVisibility(
                visible = !hidden,
                enter = slideInVertically { it } + expandVertically(),
                exit = slideOutVertically { it } + shrinkVertically()
            ) {
                NavigationBar { BottomBarItems() }
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !hidden,
                enter = slideInHorizontally { -it } + expandHorizontally(),
                exit = slideOutHorizontally { -it } + shrinkHorizontally()
            ) {
                NavigationRail { RailBarItems() }
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
            ) { sharedContent() }
        }
    }
}
