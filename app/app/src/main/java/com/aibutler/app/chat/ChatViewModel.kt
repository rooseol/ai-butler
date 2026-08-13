package com.aibutler.app.chat

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibutler.app.network.AgentName
import com.aibutler.app.network.ApiClient
import com.aibutler.app.network.FileUploadResponse
import com.aibutler.app.network.ServerConfig
import com.aibutler.app.network.SessionSummary
import com.aibutler.app.network.TranscriptEntry
import com.aibutler.app.network.WsClient
import com.aibutler.app.network.WsConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val config = ServerConfig(application)
    private var apiClient: ApiClient? = null
    private var wsClient: WsClient? = null
    private val loadedAgents = mutableSetOf<String>()

    var selectedAgent by mutableStateOf(AgentName.CLAUDE)
        private set

    var inputText by mutableStateOf("")
    var attachedFile by mutableStateOf<FileUploadResponse?>(null)
        private set
    var isUploading by mutableStateOf(false)
        private set
    var uploadError by mutableStateOf<String?>(null)
        private set

    private val _messagesByAgent = MutableStateFlow<Map<String, List<UiMessage>>>(emptyMap())
    private val _streamingByAgent = MutableStateFlow<Map<String, String>>(emptyMap())
    val wsState: StateFlow<WsConnectionState> get() = _wsState.asStateFlow()
    private val _wsState = MutableStateFlow(WsConnectionState.DISCONNECTED)

    val currentMessages: StateFlow<List<UiMessage>> get() = _currentMessages.asStateFlow()
    private val _currentMessages = MutableStateFlow<List<UiMessage>>(emptyList())

    // ---------- PC 세션 이어가기 ----------
    var showSessionPicker by mutableStateOf(false)
        private set
    var isLoadingSessions by mutableStateOf(false)
        private set
    var sessionsError by mutableStateOf<String?>(null)
        private set
    var availableSessions by mutableStateOf<List<SessionSummary>>(emptyList())
        private set
    var activeSessionId by mutableStateOf<String?>(null)
        private set
    var isLoadingTranscript by mutableStateOf(false)
        private set
    var sessionPendingDelete by mutableStateOf<SessionSummary?>(null)
        private set

    init {
        viewModelScope.launch {
            val connection = config.currentConnection() ?: return@launch
            val api = ApiClient(connection)
            apiClient = api
            val ws = WsClient(connection)
            wsClient = ws
            ws.connect()

            viewModelScope.launch { ws.state.collect { _wsState.value = it } }
            viewModelScope.launch { ws.events.collect(::handleEvent) }
            viewModelScope.launch { _messagesByAgent.collect { recomputeCurrent() } }
            viewModelScope.launch { _streamingByAgent.collect { recomputeCurrent() } }

            loadHistory(selectedAgent)
            refreshActiveSessionId()
        }
    }

    fun selectAgent(agent: AgentName) {
        selectedAgent = agent
        recomputeCurrent()
        if (agent.wire !in loadedAgents) loadHistory(agent)
        refreshActiveSessionId()
    }

    private fun refreshActiveSessionId() {
        val api = apiClient ?: return
        val agent = selectedAgent
        viewModelScope.launch {
            api.getSessions(agent.wire).onSuccess { if (selectedAgent == agent) activeSessionId = it.active }
        }
    }

    /** PC 세션 목록을 불러와 선택 다이얼로그를 엽니다. */
    fun openSessionPicker() {
        showSessionPicker = true
        val api = apiClient ?: return
        val agent = selectedAgent
        isLoadingSessions = true
        sessionsError = null
        viewModelScope.launch {
            api.getSessions(agent.wire)
                .onSuccess {
                    availableSessions = it.sessions
                    activeSessionId = it.active
                }
                .onFailure { sessionsError = it.message }
            isLoadingSessions = false
        }
    }

    fun closeSessionPicker() {
        showSessionPicker = false
    }

    /** session이 null이면 "새 대화 시작"(세션 연결 해제 + 폰 대화 기록으로 복귀)입니다. */
    fun selectSession(session: SessionSummary?) {
        val api = apiClient ?: return
        val agent = selectedAgent
        viewModelScope.launch {
            api.selectSession(agent.wire, session?.id)
                .onSuccess { result ->
                    activeSessionId = result.active
                    showSessionPicker = false

                    if (session != null) {
                        // PC에서 하던 실제 대화 내용을 불러와 화면에 표시합니다.
                        isLoadingTranscript = true
                        api.getSessionTranscript(session.id)
                            .onSuccess { transcript ->
                                val loaded = transcript.entries.mapIndexed { idx, entry ->
                                    toUiMessage(entry, idx)
                                }
                                val note = systemNote("PC 세션 이어가는 중: ${session.title}")
                                _messagesByAgent.update { it + (agent.wire to (loaded + note)) }
                            }
                            .onFailure {
                                sessionsError = it.message
                                insertSystemNote(agent, "PC 세션 이어가는 중: ${session.title} (대화 내용을 불러오지 못했습니다)")
                            }
                        isLoadingTranscript = false
                    } else {
                        // 새 대화 시작: 폰에서 오간 원래 대화 기록으로 복귀
                        loadedAgents -= agent.wire
                        loadHistoryNow(agent)
                        insertSystemNote(agent, "새 대화를 시작합니다")
                    }
                }
                .onFailure { sessionsError = it.message }
        }
    }

    fun requestDeleteSession(session: SessionSummary) {
        sessionPendingDelete = session
    }

    fun cancelDeleteSession() {
        sessionPendingDelete = null
    }

    /** 확인 다이얼로그에서 삭제를 눌렀을 때 — 로컬 세션 파일을 실제로(되돌릴 수 없게) 지웁니다. */
    fun confirmDeleteSession() {
        val session = sessionPendingDelete ?: return
        val api = apiClient ?: return
        viewModelScope.launch {
            api.deleteSession(session.id)
                .onSuccess {
                    availableSessions = availableSessions.filter { it.id != session.id }
                    if (activeSessionId == session.id) activeSessionId = null
                }
                .onFailure { sessionsError = it.message }
            sessionPendingDelete = null
        }
    }

    private fun toUiMessage(entry: TranscriptEntry, index: Int): UiMessage =
        UiMessage(id = "pc-${entry.timestamp}-$index", role = entry.role, content = entry.content, createdAt = entry.timestamp)

    private fun systemNote(text: String): UiMessage =
        UiMessage(id = "sys-${System.currentTimeMillis()}", role = "system", content = text, createdAt = System.currentTimeMillis())

    /** 서버에 저장되지 않는, 화면에만 표시되는 안내 메시지(대화 전환 표시용). */
    private fun insertSystemNote(agent: AgentName, text: String) {
        val note = UiMessage(id = "sys-${System.currentTimeMillis()}", role = "system", content = text, createdAt = System.currentTimeMillis())
        _messagesByAgent.update { it + (agent.wire to (it[agent.wire].orEmpty() + note)) }
    }

    private fun recomputeCurrent() {
        val base = _messagesByAgent.value[selectedAgent.wire].orEmpty()
        val streaming = _streamingByAgent.value[selectedAgent.wire]
        _currentMessages.value = if (streaming != null) {
            // 아직 아무 청크도 안 왔으면(streaming이 빈 문자열) "생각 중..."을 보여줍니다.
            // 이미지 생성 같은 작업은 몇 분씩 걸릴 수 있어, 진행 중임을 알리는 게 중요합니다.
            val content = streaming.ifEmpty { "🤔 생각 중... (오래 걸릴 수 있어요)" }
            base + UiMessage(id = "__streaming__", role = "agent", content = content, createdAt = Long.MAX_VALUE, isStreaming = streaming.isNotEmpty())
        } else {
            base
        }
    }

    private fun loadHistory(agent: AgentName) {
        viewModelScope.launch { loadHistoryNow(agent) }
    }

    private suspend fun loadHistoryNow(agent: AgentName) {
        val api = apiClient ?: return
        api.getMessages(agent = agent.wire, limit = 100).onSuccess { history ->
            loadedAgents += agent.wire
            _messagesByAgent.update { current ->
                current + (agent.wire to history.map {
                    UiMessage(id = it.id, role = it.role, content = it.content, createdAt = it.createdAt, fileId = it.fileId, isError = it.status == "error")
                })
            }
        }
    }

    private fun handleEvent(event: com.aibutler.app.network.WsEventRaw) {
        val agentWire = event.agent ?: return
        when (event.type) {
            "chat_saved" -> {
                val msg = UiMessage(id = event.id ?: "", role = event.role ?: "user", content = event.content.orEmpty(), createdAt = event.createdAt ?: 0L)
                _messagesByAgent.update { it + (agentWire to (it[agentWire].orEmpty() + msg)) }
                // 응답이 오기 전까지 "생각 중..."을 보여줍니다. 이미지 생성처럼 오래 걸리는 요청도
                // 화면에 아무 표시가 없으면 멈춘 것처럼 보이기 때문입니다.
                _streamingByAgent.update { it + (agentWire to (it[agentWire] ?: "")) }
            }
            "chat_chunk" -> {
                _streamingByAgent.update { it + (agentWire to ((it[agentWire] ?: "") + event.text.orEmpty())) }
            }
            "chat_done" -> {
                _streamingByAgent.update { it - agentWire }
                val msg = UiMessage(id = event.id ?: "", role = "agent", content = event.content.orEmpty(), createdAt = event.createdAt ?: 0L)
                _messagesByAgent.update { it + (agentWire to (it[agentWire].orEmpty() + msg)) }
                // 첫 메시지에서 새 세션이 자동 생성됐을 수 있으니 배지를 최신화합니다.
                if (AgentName.fromWire(agentWire) == selectedAgent) refreshActiveSessionId()
            }
            "chat_error" -> {
                _streamingByAgent.update { it - agentWire }
                val msg = UiMessage(id = "err-${System.currentTimeMillis()}", role = "agent", content = "[오류] ${event.error}", createdAt = System.currentTimeMillis(), isError = true)
                _messagesByAgent.update { it + (agentWire to (it[agentWire].orEmpty() + msg)) }
            }
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() && attachedFile == null) return
        wsClient?.sendChat(selectedAgent, text.ifBlank { "(파일 전송)" }, attachedFile?.id)
        inputText = ""
        attachedFile = null
    }

    fun pickFile(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val (name, _) = queryFileMeta(uri)
        isUploading = true
        uploadError = null
        viewModelScope.launch {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                isUploading = false
                uploadError = "파일을 읽을 수 없습니다."
                return@launch
            }
            val mime = resolver.getType(uri)
            apiClient?.uploadFile(name, mime, bytes)
                ?.onSuccess { attachedFile = it }
                ?.onFailure { uploadError = it.message }
            isUploading = false
        }
    }

    fun clearAttachedFile() {
        attachedFile = null
    }

    private fun queryFileMeta(uri: Uri): Pair<String, Long?> {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "file"
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else null
                return (name ?: "file") to size
            }
        }
        return "file" to null
    }

    override fun onCleared() {
        wsClient?.close()
        super.onCleared()
    }
}
