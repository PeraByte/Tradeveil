package com.growtic.tradeveil.data

import java.util.Date

data class TeamMember(
    val userId: String = "",
    val username: String = "",
    val profileImageUrl: String = "",
    val points: Int = 0,
    val joinDate: Date = Date()
) {
    // Simplified constructor - better for Firestore
    constructor() : this("", "", "", 0, Date())
}