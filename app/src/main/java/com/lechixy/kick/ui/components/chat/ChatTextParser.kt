package com.lechixy.kick.ui.components.chat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.model.SubscriberBadge
import com.lechixy.kick.data.model.chat.ChatIdentity

data class ParsedChatMessage(
    val annotatedString: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

data class ResolvedChatBadge(
    val key: String,
    val name: String,
    val imageUrl: String? = null,
    @DrawableRes val drawableRes: Int? = null,
    val sortOrder: Int
)

object ChatTextParser {

    private val EMOTE_REGEX = Regex("\\[emote:(\\d+)(?::([^\\]]+))?\\]")
    private val MENTION_REGEX = Regex("@[a-zA-Z0-9_]+")
    private val URL_REGEX = Regex(
        "(?:https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|[a-zA-Z0-9\\-]+\\.(?:com|org|net|edu|gov|io|gg|tv|me|co|app|live|xyz|dev)(?:/[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)",
        RegexOption.IGNORE_CASE
    )
    private const val TRAILING_URL_PUNCTUATION = ".,!?;:)%]\"'"

    fun resolveBadges(
        identity: ChatIdentity?,
        subscriberBadges: List<SubscriberBadge>
    ): List<ResolvedChatBadge> {
        if (identity == null) return emptyList()
        val result = mutableListOf<ResolvedChatBadge>()

        identity.badgesV2.forEachIndexed { index, badge ->
            if (!badge.selected) return@forEachIndexed
            val url = badge.imageUrl
            if (!url.isNullOrBlank()) {
                result.add(
                    ResolvedChatBadge(
                        key = "badge_v2_${index}_${badge.name}",
                        name = badge.name.ifBlank { "badge" },
                        imageUrl = url,
                        sortOrder = badge.sortOrder ?: (index + 1)
                    )
                )
            }
        }

        val v2BadgeNames = identity.badgesV2
            .filter { it.selected && !it.imageUrl.isNullOrBlank() }
            .map { it.name.lowercase() }
            .toSet()

        for (badgeInfo in identity.badges) {
            val badgeType = badgeInfo.type.lowercase()
            if (badgeType in v2BadgeNames) continue

            when (badgeType) {
                "broadcaster" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_broadcaster",
                            name = badgeInfo.text ?: "Broadcaster",
                            drawableRes = R.drawable.broadcaster_badge,
                            sortOrder = badgeInfo.sortOrder ?: 1
                        )
                    )
                }

                "verified" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_verified",
                            name = badgeInfo.text ?: "Verified",
                            drawableRes = R.drawable.verified_badge,
                            sortOrder = badgeInfo.sortOrder ?: 2
                        )
                    )
                }

                "moderator" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_moderator",
                            name = badgeInfo.text ?: "Moderator",
                            drawableRes = R.drawable.moderator_badge,
                            sortOrder = badgeInfo.sortOrder ?: 3
                        )
                    )
                }

                "og" -> {
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_og",
                            name = badgeInfo.text ?: "OG",
                            drawableRes = R.drawable.og_badge,
                            sortOrder = badgeInfo.sortOrder ?: 4
                        )
                    )
                }

                "sub_gifter", "subgifter", "gifter" -> {
                    val count = badgeInfo.count ?: 1
                    val drawableRes = when {
                        count >= 100 -> R.drawable.gifter_100_badge
                        count >= 50 -> R.drawable.gifter_50_badge
                        count >= 25 -> R.drawable.gifter_25_badge
                        count >= 10 -> R.drawable.gifter_10_badge
                        count >= 5 -> R.drawable.gifter_5_badge
                        else -> R.drawable.gifter_1_badge
                    }
                    result.add(
                        ResolvedChatBadge(
                            key = "badge_local_gifter_$count",
                            name = badgeInfo.text ?: "Sub Gifter",
                            drawableRes = drawableRes,
                            sortOrder = badgeInfo.sortOrder ?: 8
                        )
                    )
                }

                "subscriber" -> {
                    if (subscriberBadges.isNotEmpty()) {
                        val months = badgeInfo.count ?: 1
                        val matchedChannelBadge = subscriberBadges
                            .filter { it.months <= months && !it.badgeImage?.src.isNullOrBlank() }
                            .maxByOrNull { it.months }
                            ?: subscriberBadges
                                .filter { !it.badgeImage?.src.isNullOrBlank() }
                                .minByOrNull { it.months }

                        val subImageUrl = matchedChannelBadge?.badgeImage?.src
                        if (!subImageUrl.isNullOrBlank()) {
                            result.add(
                                ResolvedChatBadge(
                                    key = "badge_sub_${matchedChannelBadge.id}_$months",
                                    name = badgeInfo.text ?: "Subscriber",
                                    imageUrl = subImageUrl,
                                    sortOrder = badgeInfo.sortOrder ?: 9
                                )
                            )
                        }
                    }
                }
            }
        }

        return result.sortedBy { it.sortOrder }
    }

    fun containsStreamerMention(
        content: String,
        streamerUsername: String?,
        streamerSlug: String?
    ): Boolean {
        if (content.isEmpty() || !content.contains('@')) return false
        if (streamerUsername.isNullOrBlank() && streamerSlug.isNullOrBlank()) return false

        return MENTION_REGEX.findAll(content).any { matchResult ->
            val mentioned = matchResult.value.removePrefix("@")
            isStreamerMatch(mentioned, streamerUsername, streamerSlug)
        }
    }

    private fun isStreamerMatch(
        mentionedUsername: String,
        streamerUsername: String?,
        streamerSlug: String?
    ): Boolean {
        if (!streamerUsername.isNullOrBlank() && mentionedUsername.equals(streamerUsername, ignoreCase = true)) {
            return true
        }
        if (!streamerSlug.isNullOrBlank() && mentionedUsername.equals(streamerSlug, ignoreCase = true)) {
            return true
        }
        return false
    }

    @Composable
    fun parseMessage(
        senderName: String,
        senderColor: Color,
        content: String,
        fontSizeSp: Int,
        badges: List<ResolvedChatBadge> = emptyList(),
        streamerUsername: String? = null,
        streamerSlug: String? = null,
        streamerMentionBrush: Brush? = null,
        streamerMentionShadow: Shadow? = null
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val primaryColor = MaterialTheme.colorScheme.primary

        return remember(
            senderName,
            senderColor,
            content,
            safeFontSizeSp,
            badges,
            secondaryColor,
            primaryColor,
            streamerUsername,
            streamerSlug,
            streamerMentionBrush,
            streamerMentionShadow
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()

            val annotatedString = buildAnnotatedString {
                // 1. Inline Badges (so wrapped text lines start at the very beginning under the badges)
                if (badges.isNotEmpty()) {
                    val badgeHeightSp = (safeFontSizeSp + 2).sp
                    val badgeWidthSp = (safeFontSizeSp + 5).sp
                    for (badge in badges) {
                        val inlineId = badge.key
                        if (!inlineContentMap.containsKey(inlineId)) {
                            inlineContentMap[inlineId] = InlineTextContent(
                                Placeholder(
                                    width = badgeWidthSp,
                                    height = badgeHeightSp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        badge.drawableRes != null -> {
                                            Image(
                                                painter = painterResource(badge.drawableRes),
                                                contentDescription = badge.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        !badge.imageUrl.isNullOrBlank() -> {
                                            AsyncImage(
                                                model = badge.imageUrl,
                                                contentDescription = badge.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        appendSafeInlineContent(inlineId, alternateText = "[${badge.name.ifBlank { "badge" }}]")
                    }
                }

                // 2. Sender Username
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = senderColor)) {
                    append(senderName)
                }
                append(": ")

                // 3. Parse Content for Emotes and Mentions
                appendParsedContent(
                    content = content,
                    fontSizeSp = safeFontSizeSp,
                    secondaryColor = secondaryColor,
                    primaryColor = primaryColor,
                    inlineContentMap = inlineContentMap,
                    streamerUsername = streamerUsername,
                    streamerSlug = streamerSlug,
                    streamerMentionBrush = streamerMentionBrush,
                    streamerMentionShadow = streamerMentionShadow
                )
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    private fun AnnotatedString.Builder.appendSafeInlineContent(
        id: String,
        alternateText: String
    ) {
        appendInlineContent(id, alternateText = alternateText.ifBlank { "[inline]" })
    }

    @Composable
    fun parseReplyHeader(
        originalSenderName: String,
        originalSenderColor: Color,
        originalContent: String,
        fontSizeSp: Int,
        mutedColor: Color
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        val replyingToLabel = stringResource(R.string.replying_to)

        return remember(
            originalSenderName,
            originalSenderColor,
            originalContent,
            safeFontSizeSp,
            mutedColor,
            replyingToLabel
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()
            val iconSizeSp = (safeFontSizeSp + 2).sp
            val iconWidthSp = (safeFontSizeSp + 5).sp

            val annotatedString = buildAnnotatedString {
                val replyIconId = "reply_header_icon"
                inlineContentMap[replyIconId] = InlineTextContent(
                    Placeholder(
                        width = iconWidthSp,
                        height = iconSizeSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.reply_24dp),
                            contentDescription = "Reply",
                            tint = mutedColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                appendSafeInlineContent(replyIconId, alternateText = "↩ ")

                withStyle(SpanStyle(color = mutedColor)) {
                    append("$replyingToLabel ")
                }
                withStyle(SpanStyle(color = originalSenderColor, fontWeight = FontWeight.SemiBold)) {
                    append(originalSenderName)
                }
                withStyle(SpanStyle(color = mutedColor)) {
                    append(": ")
                }

                if (originalContent.isNotEmpty()) {
                    val tokens = findTokens(originalContent)
                    for (token in tokens) {
                        when (token) {
                            is ChatToken.Emote -> {
                                val inlineId = "reply_emote_${token.id}"
                                if (!inlineContentMap.containsKey(inlineId)) {
                                    val emoteUrl = "https://files.kick.com/emotes/${token.id}/fullsize"
                                    val sizeSp = (safeFontSizeSp + 2).sp
                                    inlineContentMap[inlineId] = InlineTextContent(
                                        Placeholder(
                                            width = sizeSp,
                                            height = sizeSp,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                        )
                                    ) {
                                        AsyncImage(
                                            model = emoteUrl,
                                            contentDescription = token.name,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                appendSafeInlineContent(inlineId, alternateText = ":${token.name.ifBlank { "emote" }}:")
                            }
                            is ChatToken.Mention -> {
                                withStyle(SpanStyle(color = mutedColor, fontWeight = FontWeight.Medium)) {
                                    append(token.text)
                                }
                            }
                            is ChatToken.Url -> {
                                withStyle(
                                    SpanStyle(
                                        color = mutedColor,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) {
                                    append(token.url)
                                }
                            }
                            is ChatToken.PlainText -> {
                                withStyle(SpanStyle(color = mutedColor)) {
                                    append(token.text)
                                }
                            }
                        }
                    }
                }
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    private val USER_PLACEHOLDER_REGEX = Regex("%(?:\\d+\\$)?s")

    @Composable
    fun parseUserHeaderWithBadges(
        text: String,
        textColor: Color,
        senderName: String,
        senderColor: Color,
        fontSizeSp: Int,
        badges: List<ResolvedChatBadge> = emptyList(),
        @DrawableRes leadingIconRes: Int? = null,
        leadingIconTint: Color = textColor
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        return remember(
            text,
            textColor,
            senderName,
            senderColor,
            safeFontSizeSp,
            badges,
            leadingIconRes,
            leadingIconTint
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()
            val badgeHeightSp = (safeFontSizeSp + 2).sp
            val badgeWidthSp = (safeFontSizeSp + 5).sp

            val annotatedString = buildAnnotatedString {
                if (leadingIconRes != null) {
                    val iconId = "leading_header_icon_$leadingIconRes"
                    inlineContentMap[iconId] = InlineTextContent(
                        Placeholder(
                            width = badgeWidthSp,
                            height = badgeHeightSp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(leadingIconRes),
                                contentDescription = null,
                                tint = leadingIconTint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    appendSafeInlineContent(iconId, alternateText = "[icon]")
                }

                val appendUserBlock: AnnotatedString.Builder.() -> Unit = {
                    for (badge in badges) {
                        val inlineId = badge.key
                        if (!inlineContentMap.containsKey(inlineId)) {
                            inlineContentMap[inlineId] = InlineTextContent(
                                Placeholder(
                                    width = badgeWidthSp,
                                    height = badgeHeightSp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        badge.drawableRes != null -> {
                                            Image(
                                                painter = painterResource(badge.drawableRes),
                                                contentDescription = badge.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        !badge.imageUrl.isNullOrBlank() -> {
                                            AsyncImage(
                                                model = badge.imageUrl,
                                                contentDescription = badge.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        appendSafeInlineContent(inlineId, alternateText = "[${badge.name.ifBlank { "badge" }}]")
                    }

                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = senderColor)) {
                        append(senderName)
                    }
                }

                val match = USER_PLACEHOLDER_REGEX.find(text)

                if (match != null) {
                    val prefix = text.substring(0, match.range.first)
                    val suffix = text.substring(match.range.last + 1)

                    // Placeholder öncesi (örneğin İngilizcede "Pinned by ")
                    if (prefix.isNotEmpty()) {
                        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) {
                            append(prefix)
                        }
                    }

                    appendUserBlock()

                    if (suffix.isNotEmpty()) {
                        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) {
                            append(suffix)
                        }
                    }
                } else {
                    if (text.isNotEmpty()) {
                        withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Medium)) {
                            append(text)
                        }
                        if (!text.endsWith(" ")) {
                            append(" ")
                        }
                    }
                    appendUserBlock()
                }
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    data class EventTextSegment(
        val text: String,
        val isBold: Boolean = false,
        val color: Color? = null
    )

    fun buildEventAnnotatedString(
        segments: List<EventTextSegment>,
        boldColor: Color,
        mutedColor: Color
    ): AnnotatedString = buildAnnotatedString {
        for (segment in segments) {
            if (segment.text.isEmpty()) continue
            val resolvedColor = segment.color ?: if (segment.isBold) boldColor else mutedColor
            val style = SpanStyle(
                color = resolvedColor,
                fontWeight = if (segment.isBold) FontWeight.Bold else FontWeight.Normal
            )
            withStyle(style) {
                append(segment.text)
            }
        }
    }

    @Composable
    fun parseEventHeader(
        @DrawableRes iconRes: Int,
        iconTint: Color,
        segments: List<EventTextSegment>,
        boldColor: Color,
        mutedColor: Color,
        fontSizeSp: Int
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        return remember(iconRes, iconTint, segments, boldColor, mutedColor, safeFontSizeSp) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()
            val iconSizeSp = (safeFontSizeSp + 2).sp
            val iconWidthSp = (safeFontSizeSp + 5).sp

            val annotatedString = buildAnnotatedString {
                val iconId = "event_header_icon_$iconRes"
                inlineContentMap[iconId] = InlineTextContent(
                    Placeholder(
                        width = iconWidthSp,
                        height = iconSizeSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                appendSafeInlineContent(iconId, alternateText = "[icon]")

                for (segment in segments) {
                    if (segment.text.isEmpty()) continue
                    val resolvedColor = segment.color ?: if (segment.isBold) boldColor else mutedColor
                    val style = SpanStyle(
                        color = resolvedColor,
                        fontWeight = if (segment.isBold) FontWeight.Bold else FontWeight.Normal
                    )
                    withStyle(style) {
                        append(segment.text)
                    }
                }
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    @Composable
    fun parseContentOnly(
        content: String,
        fontSizeSp: Int,
        streamerUsername: String? = null,
        streamerSlug: String? = null,
        streamerMentionBrush: Brush? = null,
        streamerMentionShadow: Shadow? = null
    ): ParsedChatMessage {
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val primaryColor = MaterialTheme.colorScheme.primary

        return remember(
            content,
            safeFontSizeSp,
            secondaryColor,
            primaryColor,
            streamerUsername,
            streamerSlug,
            streamerMentionBrush,
            streamerMentionShadow
        ) {
            val inlineContentMap = mutableMapOf<String, InlineTextContent>()

            val annotatedString = buildAnnotatedString {
                appendParsedContent(
                    content = content,
                    fontSizeSp = safeFontSizeSp,
                    secondaryColor = secondaryColor,
                    primaryColor = primaryColor,
                    inlineContentMap = inlineContentMap,
                    streamerUsername = streamerUsername,
                    streamerSlug = streamerSlug,
                    streamerMentionBrush = streamerMentionBrush,
                    streamerMentionShadow = streamerMentionShadow
                )
            }

            ParsedChatMessage(annotatedString, inlineContentMap)
        }
    }

    private fun AnnotatedString.Builder.appendParsedContent(
        content: String,
        fontSizeSp: Int,
        secondaryColor: Color,
        primaryColor: Color = secondaryColor,
        inlineContentMap: MutableMap<String, InlineTextContent>,
        streamerUsername: String?,
        streamerSlug: String?,
        streamerMentionBrush: Brush?,
        streamerMentionShadow: Shadow?
    ) {
        if (content.isEmpty()) return
        val safeFontSizeSp = fontSizeSp.coerceAtLeast(10)

        val tokens = findTokens(content)

        for (token in tokens) {
            when (token) {
                is ChatToken.Emote -> {
                    val inlineId = "emote_${token.id}"
                    if (!inlineContentMap.containsKey(inlineId)) {
                        val emoteUrl = "https://files.kick.com/emotes/${token.id}/fullsize"
                        val sizeSp = (safeFontSizeSp + 4).sp
                        inlineContentMap[inlineId] = InlineTextContent(
                            Placeholder(
                                width = sizeSp,
                                height = sizeSp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            AsyncImage(
                                model = emoteUrl,
                                contentDescription = token.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    appendSafeInlineContent(inlineId, alternateText = ":${token.name.ifBlank { "emote" }}:")
                }
                is ChatToken.Mention -> {
                    val mentionedUser = token.text.removePrefix("@")
                    val isStreamer = isStreamerMatch(mentionedUser, streamerUsername, streamerSlug)

                    val mentionSpanStyle = if (isStreamer && streamerMentionBrush != null) {
                        SpanStyle(
                            brush = streamerMentionBrush,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = streamerMentionShadow
                        )
                    } else {
                        SpanStyle(
                            color = secondaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    withStyle(mentionSpanStyle) {
                        append(token.text)
                    }
                }
                is ChatToken.Url -> {
                    withLink(
                        LinkAnnotation.Url(
                            url = token.fullUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(token.url)
                    }
                }
                is ChatToken.PlainText -> {
                    append(token.text)
                }
            }
        }
    }

    fun parseHexColor(colorStr: String?, defaultColor: Color): Color {
        if (colorStr.isNullOrBlank()) return defaultColor
        return try {
            val hex = if (colorStr.startsWith("#")) colorStr else "#$colorStr"
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            defaultColor
        }
    }

    private sealed class ChatToken {
        data class Emote(val id: String, val name: String) : ChatToken()
        data class Mention(val text: String) : ChatToken()
        data class Url(val url: String, val fullUrl: String) : ChatToken()
        data class PlainText(val text: String) : ChatToken()
    }

    private fun findTokens(text: String): List<ChatToken> {
        val tokens = mutableListOf<ChatToken>()
        var currentIndex = 0

        val emoteMatches = EMOTE_REGEX.findAll(text)
        val mentionMatches = MENTION_REGEX.findAll(text)
        val urlMatches = URL_REGEX.findAll(text)

        val allMatches = (
            emoteMatches.map { MatchItem(it.range, MatchType.EMOTE, it) } +
            mentionMatches.map { MatchItem(it.range, MatchType.MENTION, it) } +
            urlMatches.map { MatchItem(it.range, MatchType.URL, it) }
        ).sortedBy { it.range.first }

        for (matchItem in allMatches) {
            if (matchItem.range.first < currentIndex) continue

            if (matchItem.range.first > currentIndex) {
                tokens.add(ChatToken.PlainText(text.substring(currentIndex, matchItem.range.first)))
            }

            when (matchItem.type) {
                MatchType.EMOTE -> {
                    val emoteId = matchItem.matchResult.groupValues[1]
                    val emoteName = matchItem.matchResult.groupValues.getOrNull(2).orEmpty()
                    tokens.add(ChatToken.Emote(emoteId, emoteName))
                }
                MatchType.MENTION -> {
                    tokens.add(ChatToken.Mention(matchItem.matchResult.value))
                }
                MatchType.URL -> {
                    var rawUrl = matchItem.matchResult.value
                    var trailing = ""
                    while (rawUrl.isNotEmpty() && rawUrl.last() in TRAILING_URL_PUNCTUATION) {
                        trailing = rawUrl.last() + trailing
                        rawUrl = rawUrl.dropLast(1)
                    }
                    if (rawUrl.isNotEmpty()) {
                        val fullUrl = when {
                            rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
                            else -> "https://$rawUrl"
                        }
                        tokens.add(ChatToken.Url(url = rawUrl, fullUrl = fullUrl))
                        if (trailing.isNotEmpty()) {
                            tokens.add(ChatToken.PlainText(trailing))
                        }
                    }
                }
            }

            currentIndex = matchItem.range.last + 1
        }

        if (currentIndex < text.length) {
            tokens.add(ChatToken.PlainText(text.substring(currentIndex)))
        }

        return tokens
    }

    private enum class MatchType { EMOTE, MENTION, URL }
    private data class MatchItem(val range: IntRange, val type: MatchType, val matchResult: MatchResult)
}


