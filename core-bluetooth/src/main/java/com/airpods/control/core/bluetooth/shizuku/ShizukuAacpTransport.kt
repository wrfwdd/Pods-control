package com.airpods.control.core.bluetooth.shizuku

import com.airpods.control.core.aacp.AacpTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * AacpTransport backed by Shizuku GATT relay.
 * All read/write goes through Messenger IPC to ShizukuGattService
 * running in the :gatt process with shell UID.
 */
class ShizukuAacpTransport(
    private val client: ShizukuGattClient
) : AacpTransport {

    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    override val isConnected: Boolean get() = started

    fun startCollecting() {
        if (started) return
        started = true
        scope.launch {
            client.events.collect { event ->
                when (event) {
                    is ShizukuGattClient.Event.Data -> {
                        _notifications.tryEmit(event.bytes)
                    }
                    is ShizukuGattClient.Event.Disconnected -> {
                        started = false
                    }
                    else -> {}
                }
            }
        }
    }

    override suspend fun sendCommand(frame: ByteArray): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val response = CompletableDeferred<ByteArray?>()
                val job = scope.launch {
                    _notifications.collect { data ->
                        if (!response.isCompleted) response.complete(data)
                    }
                }
                client.write(frame)
                val result = withTimeout(5000L) { response.await() }
                job.cancel()
                result
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun notificationFlow(): Flow<ByteArray> = _notifications.asSharedFlow()

    fun stop() {
        started = false
        scope.cancel()
    }
}
