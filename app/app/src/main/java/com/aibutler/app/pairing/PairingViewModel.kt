package com.aibutler.app.pairing

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibutler.app.network.ApiClient
import com.aibutler.app.network.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object Connecting : PairingUiState
    data class Error(val message: String) : PairingUiState
    data object Success : PairingUiState
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val config = ServerConfig(application)

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    var host by mutableStateOf("")
    var port by mutableStateOf("8787")
    var token by mutableStateOf("")

    /** aibutler://pair?host=..&port=..&token=.. 붙여넣기/딥링크/QR 스캔 결과 처리 */
    fun applyPairingUri(uriText: String): Boolean {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return false
        val conn = ServerConfig.parsePairingUri(uri) ?: return false
        host = conn.host
        port = conn.port.toString()
        token = conn.token
        return true
    }

    /** QR 스캔 결과를 파싱해 바로 연결까지 시도합니다. */
    fun connectFromScannedUri(uriText: String, onDone: () -> Unit) {
        if (!applyPairingUri(uriText)) {
            _uiState.value = PairingUiState.Error("인식한 QR이 AI Butler 페어링 코드가 아닙니다.")
            return
        }
        connect(onDone)
    }

    fun connect(onDone: () -> Unit) {
        val portInt = port.toIntOrNull()
        if (host.isBlank() || portInt == null || token.isBlank()) {
            _uiState.value = PairingUiState.Error("호스트, 포트, 토큰을 모두 입력해주세요.")
            return
        }
        _uiState.value = PairingUiState.Connecting
        viewModelScope.launch {
            val connection = ServerConfig.Connection(host.trim(), portInt, token.trim())
            val result = ApiClient(connection).health()
            result.onSuccess {
                config.save(connection.host, connection.port, connection.token)
                _uiState.value = PairingUiState.Success
                onDone()
            }.onFailure { err ->
                _uiState.value = PairingUiState.Error("연결 실패: ${err.message ?: "알 수 없는 오류"}")
            }
        }
    }
}
