package com.aibutler.app.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibutler.app.network.FileMeta
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesScreen(viewModel: FilesViewModel = viewModel()) {
    val files by viewModel.files.collectAsState()

    LaunchedEffect(viewModel.statusMessage) {
        if (viewModel.statusMessage != null) {
            delay(2500)
            viewModel.clearStatus()
        }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            viewModel.statusMessage?.let {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(it, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
            if (files.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("주고받은 파일이 없습니다", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(files, key = { it.id }) { file ->
                        FileRow(
                            file = file,
                            isDownloading = viewModel.downloadingId == file.id,
                            onDownload = { viewModel.download(file) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: FileMeta, isDownloading: Boolean, onDownload: () -> Unit) {
    val formatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(file.filename, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${if (file.direction == "upload") "보냄" else "받음"} · ${formatter.format(Date(file.createdAt))}" +
                        (file.size?.let { " · ${formatSize(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "다운로드")
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}
