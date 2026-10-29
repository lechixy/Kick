package com.lechixy.kick.ui.components.chat

import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.model.chat.ChatSender
import org.junit.Assert.*
import org.junit.Test

class ChatMessageHighlightTest {
    @Test fun ownMessagesMatchAccountIdEvenAfterRename() {
        assertTrue(isOwnChatMessage(ChatMessage(sender = ChatSender(id = 42, username = "renamed")), 42, "oldname"))
        assertTrue(isOwnChatMessage(ChatMessage(userId = 42), 42, "me"))
    }

    @Test fun knownDifferentIdCannotMatchByName() {
        assertFalse(isOwnChatMessage(ChatMessage(sender = ChatSender(id = 99, username = "me")), 42, "me"))
    }

    @Test fun namesAreFallbackOnlyWhileAnAccountIsAvailable() {
        val message = ChatMessage(sender = ChatSender(username = "ME"))
        assertTrue(isOwnChatMessage(message, 42, "me"))
        assertFalse(isOwnChatMessage(message, null, "me"))
        assertFalse(isOwnChatMessage(ChatMessage(), 42, ""))
    }
}
