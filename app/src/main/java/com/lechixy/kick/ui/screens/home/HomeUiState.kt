package com.lechixy.kick.ui.screens.home

import com.lechixy.kick.data.model.Livestream
import com.lechixy.kick.data.model.LiveTotals

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val streams: List<Livestream> = emptyList(),
    val liveTotals: LiveTotals? = null,
    val following: FollowingLiveState = FollowingLiveState(),
    val error: String? = null
)

data class FollowingLiveState(
    val signedIn: Boolean = false,
    val streams: List<Livestream> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null
)
