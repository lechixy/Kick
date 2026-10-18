package com.lechixy.kick.ui.components.chat

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lechixy.kick.R
import com.lechixy.kick.data.model.SubscriberBadge
import com.lechixy.kick.data.model.chat.ChatMessage

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 14,
    subscriberBadges: List<SubscriberBadge> = emptyList(),
    userColors: Map<String, String> = emptyMap(),
    streamerUsername: String? = null,
    streamerSlug: String? = null,
) {
    // 1. Reward Redeemed Event
    val rewardEvent = message.rewardRedeemed
    if (message.isRewardRedeemed && rewardEvent != null) {
        val defaultPrimaryColor = MaterialTheme.colorScheme.primary
        val rewardColor = remember(rewardEvent.rewardBackgroundColor, defaultPrimaryColor) {
            ChatTextParser.parseHexColor(rewardEvent.rewardBackgroundColor, defaultPrimaryColor)
        }
        val segments = remember(rewardEvent.username, rewardEvent.rewardTitle, rewardColor) {
            listOf(
                ChatTextParser.EventTextSegment(
                    text = rewardEvent.username.ifBlank { "User" },
                    isBold = true,
                    color = rewardColor
                ),
                ChatTextParser.EventTextSegment(
                    text = " has redeemed ",
                    isBold = false
                ),
                ChatTextParser.EventTextSegment(
                    text = rewardEvent.rewardTitle,
                    isBold = true,
                    color = rewardColor
                )
            )
        }
        ChatEventBaseItem(
            iconRes = R.drawable.bubble_chart_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            segments = segments,
            bodyContent = rewardEvent.userInput.takeIf { it.isNotBlank() },
            fontSizeSp = fontSizeSp,
            stripeColor = rewardColor,
            contentPadding = 2.dp,
            streamerUsername = streamerUsername,
            streamerSlug = streamerSlug,
            modifier = modifier
        )
        return
    }

    // 2. Gifted Subscriptions Event
    val giftedEvent = message.giftedSubscriptions
    if (message.isGiftedSubscriptions && giftedEvent != null) {
        val segments = remember(giftedEvent.gifterUsername, giftedEvent.giftedUsernames) {
            buildList {
                add(ChatTextParser.EventTextSegment(giftedEvent.gifterUsername.ifBlank { "Anonymous" }, isBold = true))
                add(ChatTextParser.EventTextSegment(" gifted a sub to ", isBold = false))
                val recipients = giftedEvent.giftedUsernames.filter { it.isNotBlank() }
                if (recipients.isEmpty()) {
                    add(ChatTextParser.EventTextSegment("the community", isBold = true))
                } else {
                    recipients.forEachIndexed { index, recipient ->
                        if (index > 0) {
                            add(ChatTextParser.EventTextSegment(", ", isBold = false))
                        }
                        add(ChatTextParser.EventTextSegment(recipient, isBold = true))
                    }
                }
            }
        }
        ChatEventBaseItem(
            iconRes = R.drawable.featured_seasonal_and_gifts_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            segments = segments,
            bodyContent = null,
            fontSizeSp = fontSizeSp,
            contentPadding = 10.dp,
            streamerUsername = streamerUsername,
            streamerSlug = streamerSlug,
            modifier = modifier
        )
        return
    }

    val defaultUsernameColor = MaterialTheme.colorScheme.primary
    val usernameColor = remember(message.sender?.identity?.color, defaultUsernameColor) {
        ChatTextParser.parseHexColor(message.sender?.identity?.color, defaultUsernameColor)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val originalMessage = message.metadata?.originalMessage
        if (message.isReply && originalMessage != null && originalMessage.content.isNotBlank()) {
            val originalSenderName = message.metadata.originalSender?.username
                ?.takeIf { it.isNotBlank() }
                ?: originalMessage.sender?.username
                ?: "User"

            val rawOriginalColor = message.metadata.originalSender?.identity?.color
                ?: originalMessage.sender?.identity?.color
                ?: userColors[originalSenderName.lowercase()]

            val originalSenderColor = remember(rawOriginalColor, defaultUsernameColor) {
                ChatTextParser.parseHexColor(rawOriginalColor, defaultUsernameColor)
            }

            val replyMutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            val replyFontSizeSp = (fontSizeSp - 2).coerceAtLeast(10)

            val parsedReply = ChatTextParser.parseReplyHeader(
                originalSenderName = originalSenderName,
                originalSenderColor = originalSenderColor,
                originalContent = originalMessage.content,
                fontSizeSp = replyFontSizeSp,
                mutedColor = replyMutedColor
            )

            Text(
                text = parsedReply.annotatedString,
                inlineContent = parsedReply.inlineContent,
                fontSize = replyFontSizeSp.sp,
                lineHeight = (replyFontSizeSp + 3).sp,
                color = replyMutedColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = parsedMessage.annotatedString,
            inlineContent = parsedMessage.inlineContent,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 5).sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Reusable compact base layout for chat events (Reward Redeemed, Gifted Subscriptions, New Subscriptions, etc.).
 */
@Composable
fun ChatEventBaseItem(
    @DrawableRes iconRes: Int,
    segments: List<ChatTextParser.EventTextSegment>,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 14,
    bodyContent: String? = null,
    stripeColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: Dp = 6.dp,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    streamerUsername: String? = null,
    streamerSlug: String? = null
) {
    val boldColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val headerFontSizeSp = (fontSizeSp - 1).coerceAtLeast(11)
    val stripeWidth = 4.dp

    val parsedHeader = ChatTextParser.parseEventHeader(
        iconRes = iconRes,
        iconTint = iconTint,
        segments = segments,
        boldColor = boldColor,
        mutedColor = mutedColor,
        fontSizeSp = headerFontSizeSp
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .drawBehind {
                val strokeWidthPx = stripeWidth.toPx()
                drawRect(
                    color = stripeColor,
                    topLeft = Offset.Zero,
                    size = Size(width = strokeWidthPx, height = size.height)
                )
            }
            .padding(start = stripeWidth)
            .padding(contentPadding)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = parsedHeader.annotatedString,
            inlineContent = parsedHeader.inlineContent,
            fontSize = headerFontSizeSp.sp,
            lineHeight = (headerFontSizeSp + 4).sp,
            color = mutedColor,
            modifier = Modifier.fillMaxWidth()
        )

        if (!bodyContent.isNullOrBlank()) {
            val hasStreamerMention = remember(bodyContent, streamerUsername, streamerSlug) {
                ChatTextParser.containsStreamerMention(bodyContent, streamerUsername, streamerSlug)
            }
            val (streamerMentionBrush, streamerMentionShadow) = if (hasStreamerMention) {
                rememberStreamerMentionEffects(fontSizeSp = fontSizeSp)
            } else {
                null to null
            }

            val parsedBody = ChatTextParser.parseContentOnly(
                content = bodyContent,
                fontSizeSp = fontSizeSp,
                streamerUsername = streamerUsername,
                streamerSlug = streamerSlug,
                streamerMentionBrush = streamerMentionBrush,
                streamerMentionShadow = streamerMentionShadow
            )

            Text(
                text = parsedBody.annotatedString,
                inlineContent = parsedBody.inlineContent,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp + 5).sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun rememberStreamerMentionEffects(fontSizeSp: Int): Pair<Brush, Shadow> {
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


