package com.lechixy.kick

import com.lechixy.kick.data.remote.ConnectionNotice
import com.lechixy.kick.data.remote.ConnectionNoticeState
import com.lechixy.kick.data.remote.ConnectionQuality
import org.junit.Assert.*
import org.junit.Test

class ConnectionNoticeStateTest {
    @Test fun startingOnlineDoesNotShowAReconnectionMessage() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Online)
        assertNull(state.notice)
        state.update(ConnectionQuality.Online)
        assertNull(state.notice)
    }

    @Test fun offlinePersistsUntilInternetReturnsAndRestoredCanExpire() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Offline)
        state.expireRestored()
        assertEquals(ConnectionNotice.Offline, state.notice)
        state.update(ConnectionQuality.Online)
        assertEquals(ConnectionNotice.Restored, state.notice)
        state.expireRestored()
        assertNull(state.notice)
        state.update(ConnectionQuality.Online)
        assertNull(state.notice)
    }

    @Test fun expiredReconnectionCannotHideANewDisconnection() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Offline)
        state.update(ConnectionQuality.Online)
        state.update(ConnectionQuality.Offline)
        state.expireRestored()
        assertEquals(ConnectionNotice.Offline, state.notice)
    }

    @Test fun weakNetworkRecoveryIsVisibleWithoutLosingOfflinePriority() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Weak)
        assertEquals(ConnectionNotice.Weak, state.notice)
        state.update(ConnectionQuality.Offline)
        assertEquals(ConnectionNotice.Offline, state.notice)
        state.update(ConnectionQuality.Weak)
        assertEquals(ConnectionNotice.Weak, state.notice)
        state.update(ConnectionQuality.Online)
        assertEquals(ConnectionNotice.Restored, state.notice)
    }
}
