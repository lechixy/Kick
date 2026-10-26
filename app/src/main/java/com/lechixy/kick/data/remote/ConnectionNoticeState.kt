package com.lechixy.kick.data.remote

enum class ConnectionQuality { Online, Weak, Offline }
enum class ConnectionNotice { Weak, Offline, Restored }

/** Keeps initial connectivity and repeated callbacks from creating false reconnection messages. */
internal class ConnectionNoticeState {
    var quality: ConnectionQuality? = null
        private set
    var notice: ConnectionNotice? = null
        private set

    fun update(next: ConnectionQuality) {
        if (quality == next) return
        val previous = quality
        quality = next
        notice = when (next) {
            ConnectionQuality.Offline -> ConnectionNotice.Offline
            ConnectionQuality.Weak -> ConnectionNotice.Weak
            ConnectionQuality.Online -> if (previous == ConnectionQuality.Offline || previous == ConnectionQuality.Weak)
                ConnectionNotice.Restored else null
        }
    }

    fun expireRestored() {
        if (notice == ConnectionNotice.Restored) notice = null
    }
}
