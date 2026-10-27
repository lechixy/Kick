package com.lechixy.kick.data.account

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Application scoped account services, also usable by the existing non-Hilt player ViewModels. */
object AccountGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var auth: AuthRepository
        private set
    lateinit var following: FollowingRepository
        private set
    lateinit var chat: AccountChatRepository
        private set
    fun init(context: Context) {
        if (::auth.isInitialized) return
        auth = AuthRepository(SecureSessionStore(context.applicationContext))
        following = FollowingRepository(auth, scope)
        chat = AccountChatRepository(auth)
        scope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect { (signedIn, _) ->
                chat.clearSession()
                if (signedIn) launch {
                    try { auth.loadProfile() }
                    catch (error: Exception) { if (error is CancellationException) throw error }
                }
            }
        }
    }
}
