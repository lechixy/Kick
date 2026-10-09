package com.lechixy.kick.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class AppInformation(
    val isSuccessful: Boolean,
    val versionName: String,
    val versionCode: Long,
    val lastUpdateTimeMs: Long
)

object AppUtils {
    fun getVersionAndInfo(context: Context): AppInformation {
        try {
            val packageName = context.packageName
            val packageManager = context.packageManager

            // Retrieve package info based on the Android version
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            // 1. Get App Version Name and Version Code
            val versionName = packageInfo.versionName // e.g., "1.0.4"
            val versionCode =
                packageInfo.longVersionCode // Long value for newer APIs

            // 2. Get Last Update Time (returns Epoch timestamp in milliseconds)
            val lastUpdateTimeMs = packageInfo.lastUpdateTime

            return AppInformation(true, versionName ?: "", versionCode, lastUpdateTimeMs)

        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return AppInformation(false, "", 0, 0)
    }

    fun formatLastUpdateTime(lastUpdateTimeMs: Long): String {
        if (lastUpdateTimeMs <= 0) return "Unknown"

        val lastUpdateDate = java.util.Date(lastUpdateTimeMs)
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        return dateFormat.format(lastUpdateDate)
    }
}