package com.aibutler.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 서버(server/src)와 지원 에이전트가 정확히 일치해야 합니다. */
enum class AgentName(val wire: String, val label: String) {
    CLAUDE("claude", "Claude Code"),
    CODEX("codex", "Codex"),
    GEMINI("gemini", "Gemini"),
    ;

    companion object {
        fun fromWire(value: String): AgentName = entries.firstOrNull { it.wire == value } ?: CLAUDE
    }
}

// ---------- 채팅 메시지 (REST: GET /api/messages 는 DB row 그대로 snake_case) ----------
@Serializable
data class MessageDto(
    val id: String,
    val agent: String,
    val role: String, // "user" | "agent" | "system"
    val content: String,
    @SerialName("file_id") val fileId: String? = null,
    val status: String = "done",
    @SerialName("created_at") val createdAt: Long,
)

// ---------- WebSocket 이벤트 ----------
// 서버(ws/index.ts)가 이벤트 종류별로 다른 필드 조합을 보내므로, 모든 필드를 옵셔널로 두고
// `type`에 따라 해석합니다.
@Serializable
data class WsEventRaw(
    val type: String,
    val id: String? = null,
    val agent: String? = null,
    val role: String? = null,
    val content: String? = null,
    val text: String? = null,
    val error: String? = null,
    val createdAt: Long? = null,
)

@Serializable
data class ChatSendMessage(
    val type: String = "chat",
    val agent: String,
    val text: String,
    val fileId: String? = null,
)

// ---------- 캘린더 ----------
@Serializable
data class CalendarEventRequest(
    val title: String,
    val description: String? = null,
    val startAt: Long,
    val endAt: Long? = null,
    val allDay: Boolean = false,
)

@Serializable
data class CalendarEventResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("start_at") val startAt: Long,
    @SerialName("end_at") val endAt: Long? = null,
    @SerialName("all_day") val allDay: Int = 0,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

// ---------- 스킬 ----------
@Serializable
data class SkillRequest(
    val name: String,
    val description: String? = null,
    val agent: String = AgentName.CLAUDE.wire,
    val promptTemplate: String,
)

@Serializable
data class SkillResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    val agent: String,
    @SerialName("prompt_template") val promptTemplate: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class SkillRunResponse(
    val ok: Boolean,
    val output: String,
)

// ---------- 스케줄 ----------
@Serializable
data class ScheduleRequest(
    val skillId: String,
    val cron: String,
    val enabled: Boolean = true,
)

@Serializable
data class ScheduleResponse(
    val id: String,
    @SerialName("skill_id") val skillId: String,
    val cron: String,
    val enabled: Int,
    @SerialName("last_run_at") val lastRunAt: Long? = null,
    @SerialName("created_at") val createdAt: Long,
)

// ---------- 파일 ----------
@Serializable
data class FileUploadResponse(
    val id: String,
    val filename: String,
    val size: Long,
)

@Serializable
data class FileMeta(
    val id: String,
    val filename: String,
    val mime: String? = null,
    val size: Long? = null,
    val direction: String, // "upload"(폰->서버) | "download"(서버->폰)
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class DeviceRegisterRequest(
    val deviceId: String,
    val name: String? = null,
    val fcmToken: String? = null,
)

// ---------- PC 세션 (Claude Code 세션 이어가기) ----------
@Serializable
data class SessionSummary(
    val id: String,
    val cwd: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val approxMessageCount: Int,
)

@Serializable
data class SessionsListResponse(
    val active: String? = null,
    val sessions: List<SessionSummary> = emptyList(),
)

@Serializable
data class SessionSelectRequest(
    val agent: String,
    val sessionId: String? = null,
)

@Serializable
data class SessionSelectResponse(
    val ok: Boolean,
    val active: String? = null,
)

@Serializable
data class TranscriptEntry(
    val role: String, // "user" | "agent"
    val content: String,
    val timestamp: Long,
)

@Serializable
data class TranscriptResponse(
    val entries: List<TranscriptEntry> = emptyList(),
)

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val name: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val error: String,
)
