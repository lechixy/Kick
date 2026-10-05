package com.lechixy.kick.ui.screens.home

import com.lechixy.kick.data.model.Livestream

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val streams: List<Livestream> = emptyList(),
    val error: String? = null
)