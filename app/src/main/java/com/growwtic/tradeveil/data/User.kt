package com.growwtic.tradeveil.models

import com.google.firebase.Timestamp
import java.util.Date

data class User(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val totalProfit: Double = 0.0,
    val totalTrades: Int = 0,
    val winRate: Double = 0.0,
    val points: Long = 0L,
    var rank: Int = 0,
    val level: Long = 1L,
    val checkInStreak: Long = 0L,  // Add this field
    val teamCount: Long = 0L,      // Add this field
    val totalCheckinPoints: Long = 0L,

    // Fix timestamp fields - use Long instead of Timestamp
    val lastAdWatchTime: Long = 0L,
    val lastMessageResetTime: Long = 0L,

    // If you need other fields, add them here with default values
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Helper functions to convert Long to Date/Timestamp if needed
    fun getLastAdWatchDate(): Date = Date(lastAdWatchTime)
    fun getLastMessageResetDate(): Date = Date(lastMessageResetTime)

    // Convert to Timestamp if needed for Firebase operations
    fun getLastAdWatchTimestamp(): Timestamp = Timestamp(getLastAdWatchDate())
    fun getLastMessageResetTimestamp(): Timestamp = Timestamp(getLastMessageResetDate())
}