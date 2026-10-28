package com.lechixy.kick.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lechixy.kick.ui.components.account.AccountButton
import com.lechixy.kick.ui.components.account.ChallengesButton

@Composable
fun HomeTopBar(
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onChannelClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) { ColoredAppText() }
        Row(Modifier.widthIn(max = 232.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ChallengesButton()
            AccountButton(onChannelClick = onChannelClick, modifier = Modifier.weight(1f, fill = false))
        }
    }
}
