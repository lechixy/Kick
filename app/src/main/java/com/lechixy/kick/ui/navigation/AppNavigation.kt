package com.lechixy.kick.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lechixy.kick.R
import com.lechixy.kick.ui.screens.channel.ChannelScreen
import com.lechixy.kick.ui.screens.home.HomeScreen
import com.lechixy.kick.ui.screens.home.HomeViewModel
import com.lechixy.kick.ui.screens.settings.SettingsScreen

enum class AppDestinations(
    val label: String,
    @DrawableRes val icon: Int
) {
    HOME("Home", R.drawable.ic_home),
    SETTINGS("Settings", R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
}

@Composable
fun MainAppContainer() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Channel gibi alt detay sayfalarındayken navigation bar/rail'i gizleyebiliriz
    val showNavSuite = currentDestination?.route?.startsWith("channel/") != true

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            if (showNavSuite) {
                AppDestinations.entries.forEach { destination ->
                    item(
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == destination.name.lowercase() } == true,
                        onClick = {
                            navController.navigate(destination.name.lowercase()) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
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

            composable(
                route = "channel/{channelSlug}",
                arguments = listOf(navArgument("channelSlug") { type = NavType.StringType })
            ) { backStackEntry ->
                val slug = backStackEntry.arguments?.getString("channelSlug") ?: ""
                ChannelScreen(
                    channelSlug = slug,
                    onBackClick = { navController.popBackStack() }
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