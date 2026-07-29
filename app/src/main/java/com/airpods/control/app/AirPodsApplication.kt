package com.airpods.control.app

import android.app.Application
import com.airpods.control.core.bluetooth.shizuku.ShizukuStatus
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AirPodsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Check Shizuku availability (safe, catches all exceptions)
        ShizukuStatus.refresh(this)
        assertNoRoot()
    }

    private fun assertNoRoot() {
        val uid = android.os.Process.myUid()
        if (uid == 0) { android.util.Log.e("AirPodsControl", "Running as root! This is unsafe") }
    }
}