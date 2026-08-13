package com.aibutler.app.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class WsConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

/**
 * server/src/ws/index.ts 의 /ws 엔드포인트에 연결하는 클라이언트.
 * 재연결 로직을 내장해, 끊기면 지수 백오프로 재시도합니다.
 */
class WsClient(private val connection: ServerConfig.Connection) {

    // encodeDefaults=true 필수: ChatSendMessage.type 기본값("chat")이 있는 필드라도
    // 항상 JSON에 포함되어야 서버가 메시지 타입을 판별할 수 있습니다.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // 소켓은 유지
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var shouldReconnect = true
    private var retryDelayMs = 1000L

    val events = MutableSharedFlow<WsEventRaw>(extraBufferCapacity = 64)
    val state: StateFlow<WsConnectionState> get() = _state
    private val _state = MutableStateFlow(WsConnectionState.DISCONNECTED)

    fun connect() {
        shouldReconnect = true
        openSocket()
    }

    private fun openSocket() {
        _state.value = WsConnectionState.CONNECTING
        val request = Request.Builder().url("${connection.wsBaseUrl}?token=${connection.token}").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retryDelayMs = 1000L
                _state.value = WsConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString<WsEventRaw>(text) }
                    .onSuccess { events.tryEmit(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = WsConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = WsConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        client.dispatcher.executorService.execute {
            Thread.sleep(retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
            if (shouldReconnect) openSocket()
        }
    }

    fun sendChat(agent: AgentName, text: String, fileId: String? = null) {
        val payload = json.encodeToString(ChatSendMessage(agent = agent.wire, text = text, fileId = fileId))
        webSocket?.send(payload)
    }

    fun close() {
        shouldReconnect = false
        webSocket?.close(1000, "client closed")
        webSocket = null
        _state.value = WsConnectionState.DISCONNECTED
    }
}
