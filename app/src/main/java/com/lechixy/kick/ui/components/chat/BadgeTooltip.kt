package com.lechixy.kick.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import com.lechixy.kick.R
import kotlinx.coroutines.launch

@Composable
internal fun badgeLabel(badge: ResolvedChatBadge): String {
    val level = badge.level?.toString() ?: Regex("level[_ -]?(\\d+)", RegexOption.IGNORE_CASE).find(badge.name)?.groupValues?.get(1)
    if (level != null) return stringResource(R.string.badge_level, level)
    val id = when {
        badge.key.contains("broadcaster") || badge.name.equals("broadcaster", true) -> R.string.badge_broadcaster
        badge.key.contains("moderator") || badge.name.equals("moderator", true) -> R.string.badge_moderator
        badge.key.contains("verified") || badge.name.equals("verified", true) -> R.string.badge_verified
        badge.key.contains("founder") || badge.name.equals("founder", true) -> R.string.badge_founder
        badge.key.contains("gifter") || badge.name.equals("sub gifter", true) -> R.string.badge_gifter
        badge.key.startsWith("badge_sub_") || badge.name.equals("subscriber", true) -> R.string.badge_subscriber
        (badge.key.contains("local_vip") || badge.name.equals("vip", true)) -> R.string.badge_vip
        (badge.key.contains("local_og") || badge.name.equals("og", true)) -> R.string.badge_og
        else -> null
    }
    return id?.let { stringResource(it) } ?: badge.name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BadgeTooltip(badge: ResolvedChatBadge, content: @Composable () -> Unit) {
    // Material's shared tooltip mutator allows only one visible tooltip across chat rows.
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val label = badgeLabel(badge)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val position = remember { arrayOfNulls<Offset>(1) }
    LaunchedEffect(hovered) {
        if (hovered) state.show() else state.dismiss()
    }
    DisposableEffect(state) { onDispose { state.dismiss() } }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        state = state,
        enableUserInput = false,
        tooltip = { PlainTooltip { Text(label) } }
    ) {
        Box(Modifier.onGloballyPositioned {
            val next = it.positionInWindow()
            if (position[0] != null && position[0] != next) state.dismiss()
            position[0] = next
        }.hoverable(interaction).clickable(onClickLabel = label) { scope.launch { state.show() } }) { content() }
    }
}
