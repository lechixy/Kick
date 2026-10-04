package com.lechixy.kick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.lechixy.kick.ui.theme.KickTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KickTheme {
                KickApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun KickApp() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val navItems = listOf("Songs", "Artists", "Playlists")
    val navSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val state = rememberNavigationSuiteScaffoldState()
    val scope = rememberCoroutineScope()

    NavigationSuiteScaffold(
        state = state,
        navigationItems = {
            navItems.forEachIndexed { index, navItem ->
                NavigationSuiteItem(
                    icon = {
                        Icon(
                            painter = painterResource(if (selectedItem == index) R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                            else R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = null,
                        )
                    },
                    label = { Text(navItem) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index },
                )
            }
        },
    ) {
        // Screen content.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text =
                    "Current NavigationSuiteType: $navSuiteType\n" +
                            "Visibility: ${state.currentValue}",
                textAlign = TextAlign.Center,
            )
            Button(onClick = { scope.launch { state.toggle() } }) {
                Text("Hide/show navigation component")
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KickTheme {
        Greeting("Android")
    }
}