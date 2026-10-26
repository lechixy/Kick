package com.lechixy.kick

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.lechixy.kick.data.local.PlaybackProgressStore.init(this)
        com.lechixy.kick.data.remote.NetworkMonitor.init(this)
    }
}