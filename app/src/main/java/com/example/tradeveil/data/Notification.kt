package com.growtic.tradeveil

import java.util.Date

data class Notification(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: Date = Date(),
    val isRead: Boolean = false,
    val type: String = "" // e.g., "message", "quiz", "update"
)