package com.lechixy.kick.data.model.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ConnectPayload(
    val token: String,
    val name: String = "js"
)

@Serializable
data class CentrifugoConnectFrame(
    val connect: ConnectPayload,
    val id: Int
)

@Serializable
data class SubscribePayload(
    val channel: String,
    val flag: Int = 1,
    val token: String? = null
)

@Serializable
data class CentrifugoSubscribeFrame(
    val subscribe: SubscribePayload,
    val id: Int
)

@Serializable
data class CentrifugoPushFrame(
    val push: CentrifugoPushData? = null
)

@Serializable
data class CentrifugoPushData(
    val channel: String? = null,
    val pub: CentrifugoPublication? = null
)

@Serializable
data class CentrifugoPublication(
    val data: CentrifugoEventData? = null
)

@Serializable
data class CentrifugoEventData(
    val event: String? = null,
    val data: JsonElement? = null
)
