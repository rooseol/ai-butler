package com.aibutler.app.skills

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibutler.app.network.ApiClient
import com.aibutler.app.network.ScheduleRequest
import com.aibutler.app.network.ScheduleResponse
import com.aibutler.app.network.ServerConfig
import com.aibutler.app.network.SkillRequest
import com.aibutler.app.network.SkillResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SkillsViewModel(application: Application) : AndroidViewModel(application) {
    private val config = ServerConfig(application)
    private var apiClient: ApiClient? = null

    private val _skills = MutableStateFlow<List<SkillResponse>>(emptyList())
    val skills: StateFlow<List<SkillResponse>> = _skills.asStateFlow()

    private val _schedules = MutableStateFlow<List<ScheduleResponse>>(emptyList())
    val schedules: StateFlow<List<ScheduleResponse>> = _schedules.asStateFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var runningSkillId by mutableStateOf<String?>(null)
        private set
    var lastRunOutput by mutableStateOf<Pair<String, String>?>(null) // skillName to output
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
            api.getSkills().onSuccess { _skills.value = it }.onFailure { errorMessage = it.message }
            api.getSchedules().onSuccess { _schedules.value = it }.onFailure { errorMessage = it.message }
        }
    }

    fun createSkill(name: String, description: String?, agent: String, promptTemplate: String) {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.createSkill(SkillRequest(name = name, description = description, agent = agent, promptTemplate = promptTemplate))
                .onSuccess { refresh() }
                .onFailure { errorMessage = it.message }
        }
    }

    fun deleteSkill(id: String) {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.deleteSkill(id).onSuccess { refresh() }.onFailure { errorMessage = it.message }
        }
    }

    fun runSkill(skill: SkillResponse) {
        val api = apiClient ?: return
        runningSkillId = skill.id
        viewModelScope.launch {
            api.runSkill(skill.id)
                .onSuccess { lastRunOutput = skill.name to it.output }
                .onFailure { errorMessage = it.message }
            runningSkillId = null
        }
    }

    fun clearLastRunOutput() {
        lastRunOutput = null
    }

    fun createSchedule(skillId: String, cron: String) {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.createSchedule(ScheduleRequest(skillId = skillId, cron = cron))
                .onSuccess { refresh() }
                .onFailure { errorMessage = it.message }
        }
    }

    fun toggleSchedule(schedule: ScheduleResponse) {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.updateSchedule(schedule.id, enabled = schedule.enabled == 0)
                .onSuccess { refresh() }
                .onFailure { errorMessage = it.message }
        }
    }

    fun deleteSchedule(id: String) {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.deleteSchedule(id).onSuccess { refresh() }.onFailure { errorMessage = it.message }
        }
    }
}
