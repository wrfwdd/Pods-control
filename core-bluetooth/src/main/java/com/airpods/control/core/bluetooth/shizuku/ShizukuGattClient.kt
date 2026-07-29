package com.airpods.control.core.bluetooth.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import rikka.shizuku.Shizuku

class ShizukuGattClient(private val context: Context) {

    private var messenger: Messenger? = null
    private var bound = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    sealed class Event {
        object Connected : Event()
        object Disconnected : Event()
        object AacpReady : Event()
        data class Data(val bytes: ByteArray) : Event()
        data class Error(val msg: String) : Event()
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            scope.launch {
                when (msg.what) {
                    ShizukuGattService.MSG_CONNECTED -> _events.emit(Event.Connected)
                    ShizukuGattService.MSG_DISCONNECTED -> _events.emit(Event.Disconnected)
                    ShizukuGattService.MSG_DISCOVERED -> _events.emit(Event.AacpReady)
                    ShizukuGattService.MSG_DATA -> _events.emit(Event.Data(msg.data.getByteArray("data") ?: ByteArray(0)))
                    ShizukuGattService.MSG_ERROR -> _events.emit(Event.Error(msg.data.getString("err") ?: "unknown"))
                }
            }
        }
    }
    private val reply = Messenger(handler)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, svc: IBinder?) {
            messenger = Messenger(svc); bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            messenger = null; bound = false
        }
    }

    fun bind() {
        if (bound) return
        try {
            val cmp = ComponentName(context.packageName, ShizukuGattService::class.java.name)
            val args = Shizuku.UserServiceArgs(cmp)
                .processNameSuffix("gatt")
                .debuggable(true)
                .version(1)
            Shizuku.bindUserService(args, conn)
        } catch (_: Exception) { }
    }

    fun unbind() {
        try { Shizuku.unbindUserService(Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuGattService::class.java.name)), conn, true) } catch (_: Exception) { }
        try { context.unbindService(conn) } catch (_: Exception) { }
        bound = false; messenger = null
    }

    fun connect(addr: String) {
        val msg = Message.obtain(null, ShizukuGattService.MSG_CONNECT).apply {
            data.putString("addr", addr)
            replyTo = reply
        }
        try { messenger?.send(msg) } catch (_: Exception) { }
    }

    fun disconnect() {
        try { messenger?.send(Message.obtain(null, ShizukuGattService.MSG_DISCONNECT)) } catch (_: Exception) { }
    }

    fun discover() {
        try { messenger?.send(Message.obtain(null, ShizukuGattService.MSG_DISCOVER)) } catch (_: Exception) { }
    }

    fun write(data: ByteArray) {
        val msg = Message.obtain(null, ShizukuGattService.MSG_WRITE).apply {
            this.data.putByteArray("data", data)
        }
        try { messenger?.send(msg) } catch (_: Exception) { }
    }

    fun setNotify(enable: Boolean) {
        val msg = Message.obtain(null, ShizukuGattService.MSG_NOTIFY).apply {
            data.putBoolean("enable", enable)
        }
        try { messenger?.send(msg) } catch (_: Exception) { }
    }

    fun destroy() { unbind(); scope.cancel() }
}
