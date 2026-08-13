package com.aibutler.app.notification

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aibutler.app.network.ServerConfig
import com.aibutler.app.network.WsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 앱이 백그라운드에 있어도 WebSocket 연결을 유지해 스케줄/에이전트 응답을
 * 로컬 알림으로 받아볼 수 있게 하는 선택적 포그라운드 서비스.
 * 설정 화면에서 사용자가 켤 때만 시작됩니다.
 *
 * 참고: 이 서비스는 진짜 push(FCM)가 아니라 "앱 프로세스가 살아있는 동안" 유지되는
 * 연결입니다. 기기가 앱을 강제 종료하면 알림도 끊깁니다. 완전한 백그라운드 푸시가
 * 필요하면 docs/SETUP.md의 FCM 설정 섹션을 참고하세요.
 */
class ButlerForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var wsClient: WsClient? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.SERVICE_NOTIFICATION_ID, buildServiceNotification())
        scope.launch {
            val config = ServerConfig(applicationContext)
            val connection = config.connectionFlow.first() ?: return@launch
            val client = WsClient(connection)
            wsClient = client
            client.connect()
            client.events.collect { event ->
                if (event.type == "chat_done" && event.content != null) {
                    val agentLabel = event.agent ?: "agent"
                    Notifications.postAgentMessage(
                        applicationContext,
                        "$agentLabel 응답 도착",
                        event.content.take(200),
                    )
                } else if (event.type == "chat_error" && event.error != null) {
                    Notifications.postAgentMessage(applicationContext, "에이전트 오류", event.error)
                }
            }
        }
    }

    private fun buildServiceNotification() =
        NotificationCompat.Builder(this, Notifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("AI Butler 연결 유지 중")
            .setContentText("에이전트 응답을 백그라운드에서 수신합니다")
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wsClient?.close()
        scope.cancel()
        super.onDestroy()
    }
}
