package com.aibutler.app.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibutler.app.network.AgentName
import com.aibutler.app.network.WsConnectionState

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.currentMessages.collectAsState()
    val wsState by viewModel.wsState.collectAsState()
    val listState = rememberLazyListState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.pickFile(it) }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            Column {
                PrimaryTabRow(selectedTabIndex = AgentName.entries.indexOf(viewModel.selectedAgent)) {
                    AgentName.entries.forEach { agent ->
                        Tab(
                            selected = viewModel.selectedAgent == agent,
                            onClick = { viewModel.selectAgent(agent) },
                            text = { Text(agent.label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) { ConnectionStatusBar(wsState) }
                    IconButton(onClick = { viewModel.openSessionPicker() }) {
                        Icon(Icons.Default.History, contentDescription = "PC 세션 선택")
                    }
                }
                if (viewModel.activeSessionId != null) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "🖥 PC 세션 이어가는 중",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            ChatInputBar(
                viewModel = viewModel,
                onAttachClick = { filePicker.launch(arrayOf("*/*")) },
            )
        },
    ) { padding ->
        if (viewModel.isLoadingTranscript) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("PC 대화 불러오는 중...", modifier = Modifier.padding(top = 12.dp))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id + it.createdAt }) { message ->
                    MessageBubble(message)
                }
            }
        }
    }

    if (viewModel.showSessionPicker) {
        SessionPickerDialog(viewModel)
    }
}

@Composable
private fun ConnectionStatusBar(state: WsConnectionState) {
    val (text, color) = when (state) {
        WsConnectionState.CONNECTED -> "연결됨" to Color(0xFF2E7D32)
        WsConnectionState.CONNECTING -> "연결 중..." to Color(0xFFF9A825)
        WsConnectionState.DISCONNECTED -> "연결 끊김 (재시도 중)" to Color(0xFFC62828)
    }
    Surface(color = color.copy(alpha = 0.12f)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MessageBubble(message: UiMessage) {
    if (message.role == "system") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                message.content,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        return
    }

    val isUser = message.role == "user"
    val bubbleColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content.ifBlank { "…" },
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (message.fileId != null) {
                    Text("📎 첨부파일", color = textColor, style = MaterialTheme.typography.labelLarge)
                }
                if (message.isStreaming) {
                    Text("입력 중…", color = textColor, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(viewModel: ChatViewModel, onAttachClick: () -> Unit) {
    Surface(shadowElevation = 4.dp) {
        Column {
            viewModel.attachedFile?.let { file ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📎 ${file.filename}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(max = 240.dp))
                    IconButton(onClick = { viewModel.clearAttachedFile() }) {
                        Icon(Icons.Default.Close, contentDescription = "첨부 취소")
                    }
                }
            }
            viewModel.uploadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onAttachClick) {
                    if (viewModel.isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AttachFile, contentDescription = "파일 첨부")
                    }
                }
                OutlinedTextField(
                    value = viewModel.inputText,
                    onValueChange = { viewModel.inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지 입력...") },
                )
                IconButton(onClick = { viewModel.sendMessage() }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
                }
            }
        }
    }
}

@Composable
private fun SessionPickerDialog(viewModel: ChatViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.closeSessionPicker() },
        title = { Text("PC 세션 선택") },
        text = {
            Column {
                Text(
                    "PC에서 하던 Claude Code 대화 중 하나를 골라 폰에서 이어갈 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (viewModel.isLoadingSessions) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                viewModel.sessionsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        SessionRow(
                            title = "새 대화 시작",
                            subtitle = "PC 세션 연결 없이 새로 시작합니다",
                            selected = viewModel.activeSessionId == null,
                            onClick = { viewModel.selectSession(null) },
                        )
                    }
                    items(viewModel.availableSessions, key = { it.id }) { session ->
                        SessionRow(
                            title = session.title,
                            subtitle = "${formatRelativeTime(session.updatedAt)} · 메시지 ${session.approxMessageCount}개",
                            selected = viewModel.activeSessionId == session.id,
                            onClick = { viewModel.selectSession(session) },
                            onDelete = { viewModel.requestDeleteSession(session) },
                        )
                    }
                    if (!viewModel.isLoadingSessions && viewModel.availableSessions.isEmpty()) {
                        item {
                            Text(
                                "이 프로젝트 폴더에서 PC로 진행한 Claude Code 대화가 아직 없습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.closeSessionPicker() }) { Text("닫기") }
        },
    )

    viewModel.sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteSession() },
            title = { Text("세션을 삭제할까요?") },
            text = {
                Text(
                    "\"${session.title}\" 대화 기록을 PC에서 완전히 삭제합니다. 이 작업은 되돌릴 수 없습니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteSession() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteSession() }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SessionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        colors = if (selected) {
            androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "선택됨", tint = MaterialTheme.colorScheme.primary)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "세션 삭제", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun formatRelativeTime(epochMs: Long): String {
    val diffMinutes = (System.currentTimeMillis() - epochMs) / 60000
    return when {
        diffMinutes < 1 -> "방금 전"
        diffMinutes < 60 -> "${diffMinutes}분 전"
        diffMinutes < 60 * 24 -> "${diffMinutes / 60}시간 전"
        else -> "${diffMinutes / (60 * 24)}일 전"
    }
}

