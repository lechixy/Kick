package com.lechixy.kick.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.lechixy.kick.data.model.chat.ChatMessage

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    fontSizeSp: Int = 14,
    modifier: Modifier = Modifier
) {
    val defaultUsernameColor = MaterialTheme.colorScheme.primary
    val usernameColor = remember(message.sender?.identity?.color) {
        parseHexColor(message.sender?.identity?.color, defaultUsernameColor)
    }

    val badges = message.sender?.identity?.badgesV2.orEmpty()
    val senderName = message.sender?.username ?: "User"

    val parsedMessage = ChatTextParser.parseMessage(
        senderName = senderName,
        senderColor = usernameColor,
        content = message.content,
        fontSizeSp = fontSizeSp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Badges
        badges.forEach { badge ->
            if (!badge.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = badge.imageUrl,
                    contentDescription = badge.name,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
                )
            }
        }

        // Username & Content Text with inline Emotes and Mentions
        Text(
            text = parsedMessage.annotatedString,
            inlineContent = parsedMessage.inlineContent,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 4).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
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
