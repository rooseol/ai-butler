package com.aibutler.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aibutler.app.notification.Notifications

class AiButlerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Notifications.CHANNEL_ID,
                "AI Butler 알림",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "에이전트 응답 및 스케줄 완료 알림"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
