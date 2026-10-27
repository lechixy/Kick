package com.lechixy.kick.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lechixy.kick.R
import com.lechixy.kick.ui.components.ColoredAppText
import com.lechixy.kick.ui.settings.SettingCard
import com.lechixy.kick.util.AppUtils
import com.lechixy.kick.util.AppUtils.getVersionAndInfo

enum class SettingsDestinations(
    val title: String,
    val description: String,
    val icon: Int
) {
    GENERAL(
        "General",
        "Basic app behavior, localization, and system defaults",
        R.drawable.apps_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    CUSTOMIZATION(
        "Customization",
        "Themes, window styles, and visual tweaks",
        R.drawable.palette_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    PLAYBACK(
        "Playback",
        "Media controls, quality defaults, and auto-play options",
        R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSubsettingClick: (subsetting: SettingsDestinations) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val appInfo = getVersionAndInfo(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .widthIn(max = 840.dp), // WinUI 3 desktop/tablet responsive max width limit
            ) {
                item { com.lechixy.kick.ui.components.account.AccountPanel() }
                item {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3
                    ) {
                        SettingsDestinations.entries.forEach { dest ->
                            SettingCard(
                                title = dest.title,
                                description = dest.description,
                                icon = dest.icon,
                                onClick = {
                                    onSubsettingClick(dest)
                                }
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))
                        ColoredAppText()
                        Spacer(modifier = Modifier.height(40.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(20.dp)
                                )
                                .clip(shape = RoundedCornerShape(20.dp))
                                .clickable {
                                    Toast
                                        .makeText(context, "💖", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                .padding(25.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Made by lechixy",
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Just for you 💖",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(15.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(20.dp)
                                )
                                .clip(shape = RoundedCornerShape(20.dp))
                                .clickable {
                                    val browserIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        context.resources.getString(R.string.github).toUri()
                                    )
                                    context.startActivity(browserIntent)
                                }
                                .padding(15.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Version ${appInfo.versionName}"
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Updated at ${AppUtils.formatLastUpdateTime(appInfo.lastUpdateTimeMs)}",
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Wanna check out GitHub? Tap here!",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
