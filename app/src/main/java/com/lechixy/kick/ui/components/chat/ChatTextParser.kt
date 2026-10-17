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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

    fun resolveBadges(
        identity: ChatIdentity?,
        subscriberBadges: List<SubscriberBadge>
    ): List<ResolvedChatBadge> {
        if (identity == null) return emptyList()
        val result = mutableListOf<ResolvedChatBadge>()

        identity.badgesV2.forEachIndexed { index, badge ->
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
            .filter { !it.imageUrl.isNullOrBlank() }
            .map { it.name.lowercase() }
            .toSet()

        for (badgeInfo in identity.badges) {
            val badgeType = badgeInfo.type.lowercase()
            if (badgeType in v2BadgeNames) continue

            when (badgeType) {
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
                    val drawableRes = if (count >= 10) {
                        R.drawable.gifter_10_badge
                    } else {
                        R.drawable.gifter_1_badge
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
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val inlineContentMap = mutableMapOf<String, InlineTextContent>()

        val annotatedString = buildAnnotatedString {
            // 1. Inline Badges (so wrapped text lines start at the very beginning under the badges)
            if (badges.isNotEmpty()) {
                val badgeHeightSp = (fontSizeSp + 2).sp
                val badgeWidthSp = (fontSizeSp + 5).sp
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
                    appendInlineContent(inlineId, alternateText = "[${badge.name}]")
                }
            }

            // 2. Sender Username
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = senderColor)) {
                append(senderName)
            }
            append(": ")

            // 3. Parse Content for Emotes and Mentions
            if (content.isEmpty()) return@buildAnnotatedString

            val tokens = findTokens(content)

            for (token in tokens) {
                when (token) {
                    is ChatToken.Emote -> {
                        val inlineId = "emote_${token.id}"
                        if (!inlineContentMap.containsKey(inlineId)) {
                            val emoteUrl = "https://files.kick.com/emotes/${token.id}/fullsize"
                            val sizeSp = (fontSizeSp + 4).sp
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
                        appendInlineContent(inlineId, alternateText = ":${token.name.ifBlank { "emote" }}:")
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
                    is ChatToken.PlainText -> {
                        append(token.text)
                    }
                }
            }
        }

        return ParsedChatMessage(annotatedString, inlineContentMap)
    }

    private sealed class ChatToken {
        data class Emote(val id: String, val name: String) : ChatToken()
        data class Mention(val text: String) : ChatToken()
        data class PlainText(val text: String) : ChatToken()
    }

    private fun findTokens(text: String): List<ChatToken> {
        val tokens = mutableListOf<ChatToken>()
        var currentIndex = 0

        val emoteMatches = EMOTE_REGEX.findAll(text)
        val mentionMatches = MENTION_REGEX.findAll(text)

        val allMatches = (
            emoteMatches.map { MatchItem(it.range, MatchType.EMOTE, it) } +
            mentionMatches.map { MatchItem(it.range, MatchType.MENTION, it) }
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
            }

            currentIndex = matchItem.range.last + 1
        }

        if (currentIndex < text.length) {
            tokens.add(ChatToken.PlainText(text.substring(currentIndex)))
        }

        return tokens
    }

    private enum class MatchType { EMOTE, MENTION }
    private data class MatchItem(val range: IntRange, val type: MatchType, val matchResult: MatchResult)
}

