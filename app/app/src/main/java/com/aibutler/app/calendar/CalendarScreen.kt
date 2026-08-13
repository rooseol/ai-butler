package com.aibutler.app.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibutler.app.network.CalendarEventResponse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = viewModel()) {
    val events by viewModel.events.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "일정 추가")
            }
        },
    ) { padding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("등록된 일정이 없습니다", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    EventCard(event, onDelete = { viewModel.deleteEvent(event.id) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description, startAt, allDay ->
                viewModel.addEvent(title, description, startAt, allDay)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun EventCard(event: CalendarEventResponse, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.KOREAN) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (event.allDay == 1) "종일" else formatter.format(java.util.Date(event.startAt)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                event.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제")
            }
        }
    }
}

@Composable
private fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, startAt: Long, allDay: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var hour by remember { mutableStateOf("09") }
    var minute by remember { mutableStateOf("00") }
    var allDay by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 일정") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("설명 (선택)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 8.dp)) {
                    val millis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    Text("날짜: " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(millis)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                    Text("종일")
                }
                if (!allDay) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                            label = { Text("시(0-23)") },
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                            label = { Text("분(0-59)") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    if (!allDay) {
                        set(Calendar.HOUR_OF_DAY, hour.toIntOrNull()?.coerceIn(0, 23) ?: 9)
                        set(Calendar.MINUTE, minute.toIntOrNull()?.coerceIn(0, 59) ?: 0)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                    }
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (title.isNotBlank()) {
                    onConfirm(title.trim(), description.trim().ifBlank { null }, calendar.timeInMillis, allDay)
                }
            }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("닫기") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
