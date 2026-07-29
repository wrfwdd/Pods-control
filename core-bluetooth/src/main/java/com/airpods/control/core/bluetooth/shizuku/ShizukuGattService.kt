package com.airpods.control.core.bluetooth.shizuku

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.*
import java.util.UUID

/**
 * GATT service that runs via Shizuku in a separate :gatt process
 * with shell UID (2000), bypassing Huawei MagicUI Bluetooth filter.
 *
 * Communicates with the main app via Messenger IPC.
 */
class ShizukuGattService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null
    private var clientMessenger: Messenger? = null

    companion object {
        const val MSG_CONNECT = 1
        const val MSG_DISCONNECT = 2
        const val MSG_DISCOVER = 3
        const val MSG_WRITE = 4
        const val MSG_NOTIFY = 5
        const val MSG_CONNECTED = 100
        const val MSG_DISCONNECTED = 101
        const val MSG_DISCOVERED = 102
        const val MSG_DATA = 103
        const val MSG_ERROR = 104

        val AACP_SERVICE = UUID.fromString("74EC2170-0043-4702-A69D-40F144434D42")
        val AACP_DATA = UUID.fromString("74EC2171-0043-4702-A69D-40F144434D42")
        val AACP_NOTIFY = UUID.fromString("74EC2172-0043-4702-A69D-40F144434D42")
        val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    override fun onBind(intent: Intent?): IBinder {
        val handler = Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                MSG_CONNECT -> connect(msg)
                MSG_DISCONNECT -> disconnect()
                MSG_DISCOVER -> discover()
                MSG_WRITE -> write(msg)
                MSG_NOTIFY -> setNotify(msg)
            }
            true
        }
        return Messenger(handler).binder
    }

    @SuppressLint("MissingPermission")
    private fun connect(msg: Message) {
        try {
            clientMessenger = msg.replyTo
            val addr = msg.data.getString("addr") ?: run { sendError("No address in message"); return }
            android.util.Log.d("ShizukuGatt", "Connecting to $addr, UID=${android.os.Process.myUid()}")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { sendError("No Bluetooth adapter"); return }
            val dev = adapter.getRemoteDevice(addr) ?: run { sendError("getRemoteDevice returned null"); return }

            val cb = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, st: Int, ns: Int) {
                    send(if (ns == BluetoothProfile.STATE_CONNECTED) MSG_CONNECTED else MSG_DISCONNECTED)
                }
                override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
                    if (st == BluetoothGatt.GATT_SUCCESS && g.getService(AACP_SERVICE) != null) {
                        send(MSG_DISCOVERED)
                    } else {
                        sendError("AACP service not found")
                    }
                }
                override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                    val d = Bundle().apply { putByteArray("data", ch.value) }
                    send(MSG_DATA, d)
                }
            }

            bluetoothGatt = try { dev.connectGatt(this, false, cb, BluetoothDevice.TRANSPORT_LE) }
                catch (_: SecurityException) { null }
            if (bluetoothGatt == null) {
                try { bluetoothGatt = dev.connectGatt(this, false, cb, BluetoothDevice.TRANSPORT_AUTO) }
                    catch (_: SecurityException) { }
            }
            if (bluetoothGatt == null) sendError("connectGatt failed - LE+AUTO both null. UID=${android.os.Process.myUid()}")
        } catch (e: Exception) {
            sendError(e.message ?: "connect error")
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        try { bluetoothGatt?.disconnect() } catch (_: Exception) { }
        try { bluetoothGatt?.close() } catch (_: Exception) { }
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    private fun discover() {
        try { bluetoothGatt?.discoverServices() } catch (_: Exception) { }
    }

    @SuppressLint("MissingPermission")
    private fun write(msg: Message) {
        try {
            val data = msg.data.getByteArray("data") ?: return
            bluetoothGatt?.getService(AACP_SERVICE)?.getCharacteristic(AACP_DATA)?.let {
                it.value = data
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                bluetoothGatt?.writeCharacteristic(it)
            }
        } catch (_: Exception) { }
    }

    @SuppressLint("MissingPermission")
    private fun setNotify(msg: Message) {
        try {
            val enable = msg.data.getBoolean("enable", false)
            bluetoothGatt?.getService(AACP_SERVICE)?.getCharacteristic(AACP_NOTIFY)?.let { ch ->
                bluetoothGatt?.setCharacteristicNotification(ch, enable)
                ch.getDescriptor(CCCD)?.let { desc ->
                    desc.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    bluetoothGatt?.writeDescriptor(desc)
                }
            }
        } catch (_: Exception) { }
    }

    private fun send(what: Int, data: Bundle? = null) {
        val msg = Message.obtain(null, what)
        data?.let { msg.data = it }
        try { clientMessenger?.send(msg) } catch (_: Exception) { }
    }

    private fun sendError(err: String) {
        send(MSG_ERROR, Bundle().apply { putString("err", err) })
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }
}
