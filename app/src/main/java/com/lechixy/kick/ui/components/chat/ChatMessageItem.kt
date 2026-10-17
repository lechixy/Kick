package com.lechixy.kick.ui.components.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.lechixy.kick.data.model.SubscriberBadge
import com.lechixy.kick.data.model.chat.ChatMessage

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 14,
    subscriberBadges: List<SubscriberBadge> = emptyList(),
    streamerUsername: String? = null,
    streamerSlug: String? = null,
) {
    val defaultUsernameColor = MaterialTheme.colorScheme.primary
    val usernameColor = remember(message.sender?.identity?.color, defaultUsernameColor) {
        parseHexColor(message.sender?.identity?.color, defaultUsernameColor)
    }

    val badges = remember(message.sender?.identity, subscriberBadges) {
        ChatTextParser.resolveBadges(message.sender?.identity, subscriberBadges)
    }

    val hasStreamerMention = remember(message.content, streamerUsername, streamerSlug) {
        ChatTextParser.containsStreamerMention(message.content, streamerUsername, streamerSlug)
    }

    val (streamerMentionBrush, streamerMentionShadow) = if (hasStreamerMention) {
        rememberStreamerMentionEffects(fontSizeSp = fontSizeSp)
    } else {
        null to null
    }

    val senderName = message.sender?.username ?: "User"

    val parsedMessage = ChatTextParser.parseMessage(
        senderName = senderName,
        senderColor = usernameColor,
        content = message.content,
        fontSizeSp = fontSizeSp,
        badges = badges,
        streamerUsername = streamerUsername,
        streamerSlug = streamerSlug,
        streamerMentionBrush = streamerMentionBrush,
        streamerMentionShadow = streamerMentionShadow
    )

    Text(
        text = parsedMessage.annotatedString,
        inlineContent = parsedMessage.inlineContent,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp + 5).sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    )
}

@Composable
private fun rememberStreamerMentionEffects(fontSizeSp: Int): Pair<Brush, Shadow> {
    val spanWidthPx = with(LocalDensity.current) { (fontSizeSp * 3.5f).sp.toPx() }
    val doubleSpanWidthPx = spanWidthPx * 2f

    val transition = rememberInfiniteTransition(label = "streamer_mention_glow")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = doubleSpanWidthPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streamer_mention_offset"
    )

    val glowRadius by transition.animateFloat(
        initialValue = 12f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streamer_mention_blur"
    )

    val gradientColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )

    val brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(offset, offset),
        end = Offset(offset + spanWidthPx, offset + spanWidthPx),
        tileMode = TileMode.Mirror
    )

    val shadow = Shadow(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        offset = Offset.Zero,
        blurRadius = glowRadius
    )

    return brush to shadow
}

private fun parseHexColor(colorStr: String?, defaultColor: Color): Color {
    if (colorStr.isNullOrBlank()) return defaultColor
    return try {
        val hex = if (colorStr.startsWith("#")) colorStr else "#$colorStr"
        Color(hex.toColorInt())
    } catch (_: Exception) {
        defaultColor
    }
}

