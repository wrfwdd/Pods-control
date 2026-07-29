package com.airpods.control.core.bluetooth.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuStatus {
    @Volatile private var checked = false
    @Volatile private var available = false
    @Volatile private var hasPerm = false

    fun refresh(context: Context) {
        try {
            val ctx = context.applicationContext
            available = Shizuku.pingBinder()
            hasPerm = available && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            available = false
            hasPerm = false
        }
        checked = true
    }

    fun isAvailable() = available
    fun hasPermission() = hasPerm
    fun isReady() = available && hasPerm
    fun isChecked() = checked
}
