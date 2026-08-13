package com.aibutler.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.OutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class IdResponse(val id: String)

class ApiException(message: String, val httpCode: Int? = null) : Exception(message)

/**
 * server/src의 REST API에 대응하는 얇은 클라이언트.
 * 모든 함수는 [Result]를 반환하며, 실패 시 [ApiException]을 담습니다.
 */
class ApiClient(private val connection: ServerConfig.Connection) {

    // encodeDefaults=true: 기본값을 가진 필드도 항상 JSON에 포함시켜, 서버가 값의 "부재"와
    // "기본값"을 혼동하지 않게 합니다 (WsClient의 ChatSendMessage.type 관련 버그와 동일한 클래스의 문제 예방).
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = "${connection.restBaseUrl}$path"

    private fun Request.Builder.withAuth(): Request.Builder =
        addHeader("Authorization", "Bearer ${connection.token}")

    // 주의: execute()로 헤더까지만 받고 나면 코루틴은 원래 디스패처(보통 Main)로 돌아옵니다.
    // 이후 body.string() 같은 실제 바디 읽기(블로킹 소켓 read)를 메인 스레드에서 하면
    // NetworkOnMainThreadException이 납니다 — 응답이 작을 땐 우연히 안 걸릴 수 있어 발견이 늦었습니다.
    // 그래서 아래 모든 API 함수는 "요청~바디 읽기~JSON 파싱"을 통째로 withContext(Dispatchers.IO)로 감쌉니다.
    private fun execute(request: Request): Response = client.newCall(request).execute()

    private fun errorMessage(response: Response): String {
        val bodyText = response.body?.string().orEmpty()
        return try {
            json.decodeFromString<ApiErrorResponse>(bodyText).error
        } catch (_: Exception) {
            bodyText.ifBlank { "HTTP ${response.code}" }
        }
    }

    private suspend inline fun <reified T> getJson(path: String): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url(path)).withAuth().get().build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp), resp.code)
                json.decodeFromString(resp.body!!.string())
            }
        }
    }

    private suspend inline fun <reified B, reified T> postJson(path: String, body: B): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val payload: RequestBody = json.encodeToString(body).toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url(path)).withAuth().post(payload).build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp), resp.code)
                json.decodeFromString(resp.body!!.string())
            }
        }
    }

    private suspend inline fun <reified B, reified T> putJson(path: String, body: B): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val payload: RequestBody = json.encodeToString(body).toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(url(path)).withAuth().put(payload).build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp), resp.code)
                json.decodeFromString(resp.body!!.string())
            }
        }
    }

    private suspend fun deleteRaw(path: String): Result<OkResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url(path)).withAuth().delete().build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp), resp.code)
                json.decodeFromString(resp.body!!.string())
            }
        }
    }

    // ---------- health (인증 불필요) ----------
    suspend fun health(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("http://${connection.host}:${connection.port}/api/health").get().build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException("HTTP ${resp.code}", resp.code)
                json.decodeFromString(resp.body!!.string())
            }
        }
    }

    // ---------- chat ----------
    suspend fun getMessages(agent: String? = null, before: Long? = null, limit: Int = 50): Result<List<MessageDto>> {
        val query = buildString {
            append("?limit=$limit")
            if (agent != null) append("&agent=$agent")
            if (before != null) append("&before=$before")
        }
        return getJson("/messages$query")
    }

    // ---------- calendar ----------
    suspend fun getCalendarEvents(): Result<List<CalendarEventResponse>> = getJson("/calendar/events")
    suspend fun createCalendarEvent(req: CalendarEventRequest): Result<IdResponse> = postJson("/calendar/events", req)
    suspend fun updateCalendarEvent(id: String, req: CalendarEventRequest): Result<OkResponse> = putJson("/calendar/events/$id", req)
    suspend fun deleteCalendarEvent(id: String): Result<OkResponse> = deleteRaw("/calendar/events/$id")

    // ---------- skills ----------
    suspend fun getSkills(): Result<List<SkillResponse>> = getJson("/skills")
    suspend fun createSkill(req: SkillRequest): Result<IdResponse> = postJson("/skills", req)
    suspend fun deleteSkill(id: String): Result<OkResponse> = deleteRaw("/skills/$id")
    suspend fun runSkill(id: String): Result<SkillRunResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/skills/$id/run")).withAuth().post("".toRequestBody(null)).build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp), resp.code)
                json.decodeFromString(resp.body!!.string())
            }
        }
    }

    // ---------- schedules ----------
    suspend fun getSchedules(): Result<List<ScheduleResponse>> = getJson("/schedules")
    suspend fun createSchedule(req: ScheduleRequest): Result<IdResponse> = postJson("/schedules", req)
    suspend fun deleteSchedule(id: String): Result<OkResponse> = deleteRaw("/schedules/$id")

    @Serializable
    data class ScheduleUpdateRequest(val cron: String? = null, val enabled: Boolean? = null)
    suspend fun updateSchedule(id: String, cron: String? = null, enabled: Boolean? = null): Result<OkResponse> =
        putJson("/schedules/$id", ScheduleUpdateRequest(cron, enabled))

    // ---------- 세션 (PC의 Claude Code 세션 이어가기) ----------
    suspend fun getSessions(agent: String): Result<SessionsListResponse> = getJson("/sessions?agent=$agent")
    suspend fun selectSession(agent: String, sessionId: String?): Result<SessionSelectResponse> =
        postJson("/sessions/select", SessionSelectRequest(agent, sessionId))
    suspend fun getSessionTranscript(sessionId: String): Result<TranscriptResponse> =
        getJson("/sessions/$sessionId/transcript")
    suspend fun deleteSession(sessionId: String): Result<OkResponse> = deleteRaw("/sessions/$sessionId")

    // ---------- files ----------
    suspend fun getFiles(): Result<List<FileMeta>> = getJson("/files")

    suspend fun uploadFile(filename: String, mime: String?, bytes: ByteArray): Result<FileUploadResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = (mime ?: "application/octet-stream").toMediaTypeOrNull()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, bytes.toRequestBody(mediaType))
                .build()
            val request = Request.Builder().url(url("/files")).withAuth().post(body).build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp), resp.code)
                json.decodeFromString(resp.body!!.string())
            }
        }
    }

    /** 파일을 다운로드해 [output]에 스트리밍합니다. 성공 시 서버가 응답한 파일명을 반환합니다. */
    suspend fun downloadFileTo(id: String, output: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/files/$id")).withAuth().get().build()
            execute(request).use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp), resp.code)
                resp.body!!.byteStream().use { input -> input.copyTo(output) }
                Unit
            }
        }
    }

    // ---------- devices ----------
    suspend fun registerDevice(deviceId: String, name: String?, fcmToken: String? = null): Result<IdResponse> =
        postJson("/devices/register", DeviceRegisterRequest(deviceId, name, fcmToken))
}
