package com.lechixy.kick.data.account

import com.lechixy.kick.data.model.chat.PointsUpdatedEvent

/** The authenticated private channel identifies the recipient even when user_id is omitted. */
object ChannelPointsPolicy {
    fun accepts(event: PointsUpdatedEvent, channelId: Long, userId: Long?): Boolean =
        userId != null && event.channelId == channelId && (event.userId == null || event.userId == userId)

    fun acceptsPublication(source: String?, userId: Long?): Boolean =
        userId != null && source == "private-channelpoints-$userId"
}
