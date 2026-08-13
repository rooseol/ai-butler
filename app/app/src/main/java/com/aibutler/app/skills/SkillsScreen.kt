package com.aibutler.app.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aibutler.app.network.AgentName
import com.aibutler.app.network.ScheduleResponse
import com.aibutler.app.network.SkillResponse

@Composable
fun SkillsScreen(viewModel: SkillsViewModel = viewModel()) {
    val skills by viewModel.skills.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    var showAddSkill by remember { mutableStateOf(false) }
    var showAddSchedule by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSkill = true }) {
                Icon(Icons.Default.Add, contentDescription = "스킬 추가")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader("스킬 (저장된 프롬프트)") }
            if (skills.isEmpty()) {
                item { Text("아직 등록된 스킬이 없습니다.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(skills, key = { it.id }) { skill ->
                SkillCard(
                    skill = skill,
                    isRunning = viewModel.runningSkillId == skill.id,
                    onRun = { viewModel.runSkill(skill) },
                    onDelete = { viewModel.deleteSkill(skill.id) },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader("스케줄 (자동 실행)")
                    TextButton(onClick = { showAddSchedule = true }, enabled = skills.isNotEmpty()) { Text("+ 추가") }
                }
            }
            if (schedules.isEmpty()) {
                item { Text("아직 등록된 스케줄이 없습니다.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(schedules, key = { it.id }) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    skillName = skills.firstOrNull { it.id == schedule.skillId }?.name ?: schedule.skillId,
                    onToggle = { viewModel.toggleSchedule(schedule) },
                    onDelete = { viewModel.deleteSchedule(schedule.id) },
                )
            }
        }
    }

    viewModel.lastRunOutput?.let { (skillName, output) ->
        AlertDialog(
            onDismissRequest = { viewModel.clearLastRunOutput() },
            title = { Text("\"$skillName\" 실행 결과") },
            text = { Text(output) },
            confirmButton = { TextButton(onClick = { viewModel.clearLastRunOutput() }) { Text("닫기") } },
        )
    }

    if (showAddSkill) {
        AddSkillDialog(
            onDismiss = { showAddSkill = false },
            onConfirm = { name, description, agent, prompt ->
                viewModel.createSkill(name, description, agent, prompt)
                showAddSkill = false
            },
        )
    }

    if (showAddSchedule) {
        AddScheduleDialog(
            skills = skills,
            onDismiss = { showAddSchedule = false },
            onConfirm = { skillId, cron ->
                viewModel.createSchedule(skillId, cron)
                showAddSchedule = false
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SkillCard(skill: SkillResponse, isRunning: Boolean, onRun: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium)
                Text("[${skill.agent}] ${skill.promptTemplate}", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                IconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, contentDescription = "실행") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
        }
    }
}

@Composable
private fun ScheduleCard(schedule: ScheduleResponse, skillName: String, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(skillName, style = MaterialTheme.typography.titleMedium)
                Text("cron: ${schedule.cron}", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = schedule.enabled == 1, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
        }
    }
}

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?, agent: String, prompt: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var selectedAgent by remember { mutableStateOf(AgentName.CLAUDE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 스킬") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("설명 (선택)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text("에이전트", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    AgentName.entries.forEach { agent ->
                        FilterChip(
                            selected = selectedAgent == agent,
                            onClick = { selectedAgent = agent },
                            label = { Text(agent.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("프롬프트 템플릿") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && prompt.isNotBlank()) {
                    onConfirm(name.trim(), description.trim().ifBlank { null }, selectedAgent.wire, prompt.trim())
                }
            }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AddScheduleDialog(
    skills: List<SkillResponse>,
    onDismiss: () -> Unit,
    onConfirm: (skillId: String, cron: String) -> Unit,
) {
    var skillExpanded by remember { mutableStateOf(false) }
    var selectedSkill by remember { mutableStateOf(skills.firstOrNull()) }
    var cron by remember { mutableStateOf("0 9 * * *") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 스케줄") },
        text = {
            Column {
                Text("스킬", style = MaterialTheme.typography.labelLarge)
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = { skillExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedSkill?.name ?: "스킬 선택")
                    }
                    DropdownMenu(expanded = skillExpanded, onDismissRequest = { skillExpanded = false }) {
                        skills.forEach { skill ->
                            DropdownMenuItem(text = { Text(skill.name) }, onClick = { selectedSkill = skill; skillExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    label = { Text("cron 표현식 (분 시 일 월 요일)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "예: \"0 9 * * *\" = 매일 오전 9시, \"*/30 * * * *\" = 30분마다",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selectedSkill?.let { onConfirm(it.id, cron.trim()) }
            }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
