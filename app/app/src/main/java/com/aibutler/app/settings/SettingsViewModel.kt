package com.aibutler.app.settings

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibutler.app.network.ServerConfig
import com.aibutler.app.notification.ButlerForegroundService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val config = ServerConfig(application)

    val connection: StateFlow<ServerConfig.Connection?> = config.connectionFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    val notificationsEnabled: StateFlow<Boolean> = config.notificationsEnabledFlow
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            config.setNotificationsEnabled(enabled)
            val context = getApplication<Application>()
            val intent = Intent(context, ButlerForegroundService::class.java)
            if (enabled) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }

    fun unpair(onDone: () -> Unit) {
        viewModelScope.launch {
            setNotificationsEnabled(false)
            config.clear()
            onDone()
        }
    }
}
