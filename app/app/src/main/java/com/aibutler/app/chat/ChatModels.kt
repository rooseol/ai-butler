package com.aibutler.app.chat

data class UiMessage(
    val id: String,
    val role: String, // "user" | "agent" | "system"
    val content: String,
    val createdAt: Long,
    val fileId: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
)
