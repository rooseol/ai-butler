package com.aibutler.app.calendar

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aibutler.app.network.ApiClient
import com.aibutler.app.network.CalendarEventRequest
import com.aibutler.app.network.CalendarEventResponse
import com.aibutler.app.network.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val config = ServerConfig(application)
    private var apiClient: ApiClient? = null

    private val _events = MutableStateFlow<List<CalendarEventResponse>>(emptyList())
    val events: StateFlow<List<CalendarEventResponse>> = _events.asStateFlow()

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
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
        isLoading = true
        viewModelScope.launch {
            api.getCalendarEvents()
                .onSuccess { _events.value = it.sortedBy { e -> e.startAt } }
                .onFailure { errorMessage = it.message }
            isLoading = false
        }
    }

    fun addEvent(title: String, description: String?, startAt: Long, allDay: Boolean) {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.createCalendarEvent(CalendarEventRequest(title = title, description = description, startAt = startAt, allDay = allDay))
                .onSuccess { refresh() }
                .onFailure { errorMessage = it.message }
        }
    }

    fun deleteEvent(id: String) {
        val api = apiClient ?: return
        viewModelScope.launch {
            api.deleteCalendarEvent(id)
                .onSuccess { refresh() }
                .onFailure { errorMessage = it.message }
        }
    }
}
