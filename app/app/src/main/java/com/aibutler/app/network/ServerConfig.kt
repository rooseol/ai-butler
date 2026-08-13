package com.aibutler.app.network

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "server_config")

/** 페어링된 서버 연결 정보를 저장/조회합니다. */
class ServerConfig(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val TOKEN = stringPreferencesKey("token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    data class Connection(val host: String, val port: Int, val token: String) {
        val restBaseUrl get() = "http://$host:$port/api"
        val wsBaseUrl get() = "ws://$host:$port/ws"
    }

    val connectionFlow: Flow<Connection?> = context.dataStore.data.map { prefs ->
        val host = prefs[Keys.HOST]
        val port = prefs[Keys.PORT]
        val token = prefs[Keys.TOKEN]
        if (host != null && port != null && token != null) Connection(host, port, token) else null
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: false }

    suspend fun currentConnection(): Connection? = connectionFlow.first()

    suspend fun save(host: String, port: Int, token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = host
            prefs[Keys.PORT] = port
            prefs[Keys.TOKEN] = token
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    /** 기기를 식별할 안정적인 랜덤 ID (최초 1회 생성 후 재사용). */
    suspend fun deviceId(): String {
        val existing = context.dataStore.data.map { it[Keys.DEVICE_ID] }.first()
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    /** aibutler://pair?host=..&port=..&token=.. 형태의 페어링 URI를 파싱합니다. */
    companion object {
        fun parsePairingUri(uri: android.net.Uri): Connection? {
            val host = uri.getQueryParameter("host") ?: return null
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
            val token = uri.getQueryParameter("token") ?: return null
            return Connection(host, port, token)
        }
    }
}
