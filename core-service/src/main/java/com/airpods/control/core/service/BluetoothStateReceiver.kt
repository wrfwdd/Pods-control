package com.airpods.control.core.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Monitors Bluetooth adapter and ACL connection events.
 * Restarts the foreground service when Bluetooth state changes or
 * when the device boots.
 */
class BluetoothStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.STATE_OFF
                )
                if (state == BluetoothAdapter.STATE_ON) {
                    startService(context)
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // ACL connection established -
                // the foreground service handles GATT discovery and AACP setup
                startService(context)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // Device disconnected - state machine handles cleanup
                // Service stays alive for reconnection
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                startService(context)
            }
        }
    }

    private fun startService(context: Context) {
        val serviceIntent = Intent(context, AirPodsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
