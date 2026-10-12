package com.lechixy.kick.ui.components.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

data class ParsedChatMessage(
    val annotatedString: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

object ChatTextParser {

    private val EMOTE_REGEX = Regex("\\[emote:(\\d+)(?::([^\\]]+))?\\]")
    private val MENTION_REGEX = Regex("@[a-zA-Z0-9_]+")

    @Composable
    fun parseMessage(
        senderName: String,
        senderColor: Color,
        content: String,
        fontSizeSp: Int
    ): ParsedChatMessage {
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val inlineContentMap = mutableMapOf<String, InlineTextContent>()

        val annotatedString = buildAnnotatedString {
            // 1. Sender Username
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = senderColor)) {
                append(senderName)
            }
            append(": ")

            // 2. Parse Content for Emotes and Mentions
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
                        withStyle(SpanStyle(color = secondaryColor, fontWeight = FontWeight.Bold)) {
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
