package com.example.webrtc

import android.util.Log
import com.example.data.model.SignalingPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class SignalingClient(
    private val clientScope: CoroutineScope
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SignalingPayload::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive WebSocket
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentUrl: String = ""
    private var shouldReconnect: Boolean = true

    private val _messages = MutableSharedFlow<SignalingPayload>(replay = 0, extraBufferCapacity = 64)
    val messages: SharedFlow<SignalingPayload> = _messages.asSharedFlow()

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    val isConnected: SharedFlow<Boolean> = _isConnected.asSharedFlow()

    fun connect(url: String) {
        if (url.isBlank()) return
        currentUrl = url
        shouldReconnect = true
        initiateConnection()
    }

    private fun initiateConnection() {
        disconnect(isReconnecting = true)

        Log.d(TAG, "Connecting to signaling server: $currentUrl")
        val request = Request.Builder()
            .url(currentUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to $currentUrl")
                clientScope.launch {
                    _isConnected.emit(true)
                }
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val payload = adapter.fromJson(text)
                    if (payload != null) {
                        Log.d(TAG, "Received message type: ${payload.type}")
                        clientScope.launch {
                            _messages.emit(payload)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse signaling message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                handleDisconnection()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                handleDisconnection()
            }
        })
    }

    private fun handleDisconnection() {
        stopHeartbeat()
        clientScope.launch {
            _isConnected.emit(false)
        }
        if (shouldReconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = clientScope.launch(Dispatchers.IO) {
            delay(3000)
            if (shouldReconnect && isActive) {
                Log.d(TAG, "Attempting reconnect to signaling server...")
                initiateConnection()
            }
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = clientScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(25000)
                send(SignalingPayload(type = SignalingPayload.TYPE_PING))
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun send(payload: SignalingPayload) {
        val socket = webSocket
        if (socket != null) {
            try {
                val json = adapter.toJson(payload)
                socket.send(json)
            } catch (e: Exception) {
                Log.e(TAG, "Error serializing or sending signaling payload: ${payload.type}", e)
            }
        } else {
            Log.w(TAG, "Cannot send message ${payload.type}; WebSocket is null")
        }
    }

    fun disconnect(isReconnecting: Boolean = false) {
        if (!isReconnecting) {
            shouldReconnect = false
            reconnectJob?.cancel()
        }
        stopHeartbeat()
        try {
            webSocket?.close(1000, "Disconnect requested")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket", e)
        }
        webSocket = null
    }

    companion object {
        private const val TAG = "SignalingClient"
    }
}
