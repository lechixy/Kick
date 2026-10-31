package com.lechixy.kick.ui.screens.channel

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Settle at the nearest page: release velocity must not skip past the closest tab. */
@Composable
internal fun ChannelTabPager(state: PagerState, tabs: List<ChannelTab>, modifier: Modifier = Modifier,
    content: @Composable (ChannelTab) -> Unit) {
    val snapping = PagerDefaults.flingBehavior(state, snapPositionalThreshold = .5f)
    val nearest = remember(snapping) {
        object : TargetedFlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingDistanceUpdated: (Float) -> Unit): Float =
                with(snapping) { performFling(0f, onRemainingDistanceUpdated) }
        }
    }
    HorizontalPager(state = state, modifier = modifier, beyondViewportPageCount = 1,
        flingBehavior = nearest, key = { tabs[it].name }) { content(tabs[it]) }
}
