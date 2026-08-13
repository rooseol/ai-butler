package com.aibutler.app.files

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibutler.app.network.ApiClient
import com.aibutler.app.network.FileMeta
import com.aibutler.app.network.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class FilesViewModel(application: Application) : AndroidViewModel(application) {
    private val config = ServerConfig(application)
    private var apiClient: ApiClient? = null

    private val _files = MutableStateFlow<List<FileMeta>>(emptyList())
    val files: StateFlow<List<FileMeta>> = _files.asStateFlow()

    var statusMessage by mutableStateOf<String?>(null)
        private set
    var downloadingId by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val connection = config.currentConnection() ?: return@launch
            apiClient = ApiClient(connection)
            refresh()
        }
    }

    fun refresh() {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.getFiles().onSuccess { _files.value = it.sortedByDescending { f -> f.createdAt } }
                .onFailure { statusMessage = "목록을 불러오지 못했습니다: ${it.message}" }
        }
    }

    fun clearStatus() {
        statusMessage = null
    }

    fun download(file: FileMeta) {
        val api = apiClient ?: return
        val context = getApplication<Application>()
        downloadingId = file.id
        viewModelScope.launch {
            val result = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, file.mime ?: "application/octet-stream")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AIButler")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("다운로드 경로를 만들 수 없습니다.")
                    resolver.openOutputStream(uri)?.use { out ->
                        api.downloadFileTo(file.id, out).getOrThrow()
                    } ?: error("출력 스트림을 열 수 없습니다.")
                } else {
                    // API 26-28: 별도 저장소 권한 없이 쓸 수 있는 앱 전용 외부 저장 영역을 사용합니다.
                    // (파일 관리자 앱에서 Android/data/com.aibutler.app/files/Download 경로로 접근 가능)
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: error("저장 공간을 찾을 수 없습니다.")
                    dir.mkdirs()
                    val target = File(dir, file.filename)
                    FileOutputStream(target).use { out -> api.downloadFileTo(file.id, out).getOrThrow() }
                }
            }
            result.onSuccess { statusMessage = "다운로드 완료: ${file.filename}" }
            result.onFailure { statusMessage = "다운로드 실패: ${it.message}" }
            downloadingId = null
        }
    }
}
