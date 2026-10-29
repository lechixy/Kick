package com.lechixy.kick.ui.screens.channel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

internal class OfflineBannerScrollConnection(
    private val enabled: () -> Boolean,
    private val atTop: () -> Boolean,
    private val onCollapsedChange: (Boolean) -> Unit
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (enabled() && source == NestedScrollSource.UserInput && available.y < 0f) onCollapsedChange(true)
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Direction changes in the middle of the page must never reopen the banner.
        // Include fling movement so returning to the top by momentum also restores it.
        if (enabled() && consumed.y + available.y > 0f && atTop()) onCollapsedChange(false)
        return Offset.Zero
    }
}
