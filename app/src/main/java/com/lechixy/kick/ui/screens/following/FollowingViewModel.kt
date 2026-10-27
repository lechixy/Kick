package com.lechixy.kick.ui.screens.following

import androidx.lifecycle.ViewModel
import com.lechixy.kick.data.account.AccountGraph

class FollowingViewModel : ViewModel() {
    val uiState = AccountGraph.following.state
    val session = AccountGraph.auth.session
    fun refresh() = AccountGraph.following.refresh()
    fun loadMore() = AccountGraph.following.loadMore()
}
