package com.lechixy.kick.data.remote

import android.util.Log
import com.lechixy.kick.data.model.chat.CentrifugoConnectFrame
import com.lechixy.kick.data.model.chat.CentrifugoPushFrame
import com.lechixy.kick.data.model.chat.CentrifugoSubscribeFrame
import com.lechixy.kick.data.model.chat.ChatMessage
import com.lechixy.kick.data.model.chat.ConnectPayload
import com.lechixy.kick.data.model.chat.RealtimeAuthRequest
import com.lechixy.kick.data.model.chat.RealtimeClientInfo
import com.lechixy.kick.data.model.chat.RealtimeConnectionRequest
import com.lechixy.kick.data.model.chat.SubscribePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class ChatConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class KickChatWebSocketClient(
    private val api: KickApi = KickNetwork.api
) {
    private val tag = "KickChatWS"
    private val maxMessagesBuffer = 250

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + Job())

    private var currentChannelId: Long? = null
    private var currentChatroomId: Long? = null
    private var reconnectJob: Job? = null
    private var isIntentionallyClosed = false

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _connectionState = MutableStateFlow(ChatConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    fun connect(channelId: Long, chatroomId: Long) {
        if (currentChannelId == channelId && currentChatroomId == chatroomId && _connectionState.value == ChatConnectionState.CONNECTED) {
            return
        }

        disconnectInternal()

        currentChannelId = channelId
        currentChatroomId = chatroomId
        isIntentionallyClosed = false

        clientScope.launch {
            _connectionState.value = ChatConnectionState.CONNECTING
            startConnectionFlow(channelId, chatroomId)
        }
    }

    private suspend fun startConnectionFlow(channelId: Long, chatroomId: Long) {
        try {
            val guestClientId = (10000000..99999999).random().toString()

            val connectionReq = RealtimeConnectionRequest(
                client = RealtimeClientInfo(id = guestClientId, type = "web"),
                capabilities = com.lechixy.kick.data.model.chat.RealtimeCapabilities(
                    acceptedProviders = listOf(
                        com.lechixy.kick.data.model.chat.ProviderCapability("pusher"),
                        com.lechixy.kick.data.model.chat.ProviderCapability("centrifugo")
                    )
                )
            )

            val connectionRes = try {
                api.getChannelChatConnection(channelId, connectionReq)
            } catch (_: Exception) {
                api.getRealtimeConnection(connectionReq)
            }

            val wsUrl = connectionRes.data?.connections?.firstOrNull()?.credentials?.url
                ?.takeIf { it.isNotBlank() }
                ?: "wss://realtime.us-east-1.platform.kick.com/connection/websocket"

            val authReq = RealtimeAuthRequest(clientId = guestClientId)
            val authRes = api.getRealtimeAuth(authReq)
            val token = authRes.data?.token.orEmpty()

            val request = Request.Builder()
                .url(wsUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Origin", "https://kick.com")
                .build()

            val listener = createWebSocketListener(token, guestClientId, channelId, chatroomId)
            webSocket = client.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate chat connection: ${e.message}", e)
            _connectionState.value = ChatConnectionState.ERROR
            scheduleReconnect(channelId, chatroomId)
        }
    }

    private fun createWebSocketListener(
        token: String,
        clientId: String,
        channelId: Long,
        chatroomId: Long
    ) = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(tag, "WebSocket connected successfully")

            // Frame 1: Connect
            val connectFrame = CentrifugoConnectFrame(
                connect = ConnectPayload(token = token, name = "js"),
                id = 1
            )
            webSocket.send(json.encodeToString(CentrifugoConnectFrame.serializer(), connectFrame))

            // Subscription Frames
            val subscriptions = listOf(
                "chatroom_$chatroomId" to 2,
                "channel_$channelId" to 3,
                "channel.$channelId" to 4,
                "chatrooms.$chatroomId" to 5,
                "predictions-channel-$channelId" to 6,
                "chatrooms.$chatroomId.v2" to 7,
                "private-userfeed.$clientId" to 8,
                "private-$clientId" to 9,
                "private-channelpoints-$clientId" to 10
            )

            for ((ch, id) in subscriptions) {
                val subToken = if (ch.startsWith("private-")) token else null
                val subFrame = CentrifugoSubscribeFrame(
                    subscribe = SubscribePayload(channel = ch, flag = 1, token = subToken),
                    id = id
                )
                webSocket.send(json.encodeToString(CentrifugoSubscribeFrame.serializer(), subFrame))
            }

            _connectionState.value = ChatConnectionState.CONNECTED
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val trimmed = text.trim()
            if (trimmed == "{}" || trimmed.isEmpty()) {
                webSocket.send("{}")
                if (_connectionState.value != ChatConnectionState.CONNECTED) {
                    _connectionState.value = ChatConnectionState.CONNECTED
                }
                return
            }

            try {
                val pushFrame = json.decodeFromString<CentrifugoPushFrame>(trimmed)
                val eventData = pushFrame.push?.pub?.data ?: return
                val eventType = eventData.event

                if (eventType == "App\\Events\\ChatMessageEvent" || eventType == "ChatMessageEvent" || pushFrame.push.channel?.startsWith("chatrooms.") == true) {
                    val rawData = eventData.data ?: return
                    val chatMessage: ChatMessage? = when {
                        rawData is JsonPrimitive && rawData.isString -> {
                            json.decodeFromString<ChatMessage>(rawData.content)
                        }
                        rawData is JsonObject -> {
                            json.decodeFromJsonElement<ChatMessage>(rawData)
                        }
                        else -> null
                    }

                    if (chatMessage != null && chatMessage.content.isNotBlank()) {
                        addMessage(chatMessage)
                    }
                }
            } catch (_: Exception) {
                // Ignore non-chat or unparseable frames
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(tag, "WebSocket failure: ${t.message}")
            if (!isIntentionallyClosed) {
                _connectionState.value = ChatConnectionState.ERROR
                scheduleReconnect(channelId, chatroomId)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(tag, "WebSocket closed: $reason")
            if (!isIntentionallyClosed) {
                _connectionState.value = ChatConnectionState.DISCONNECTED
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        val currentList = _messages.value
        val updatedList = if (currentList.size >= maxMessagesBuffer) {
            currentList.drop(currentList.size - maxMessagesBuffer + 1) + message
        } else {
            currentList + message
        }
        _messages.value = updatedList
    }

    private fun scheduleReconnect(channelId: Long, chatroomId: Long) {
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            delay(3000)
            if (!isIntentionallyClosed && currentChannelId == channelId && currentChatroomId == chatroomId) {
                Log.d(tag, "Attempting reconnect...")
                startConnectionFlow(channelId, chatroomId)
            }
        }
    }

    fun disconnect() {
        isIntentionallyClosed = true
        disconnectInternal()
    }

    private fun disconnectInternal() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Disconnect requested")
        webSocket = null
        currentChannelId = null
        currentChatroomId = null
        _connectionState.value = ChatConnectionState.DISCONNECTED
        _messages.value = emptyList()
    }
}
