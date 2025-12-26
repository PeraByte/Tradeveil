// Transfer.kt
package com.growtiic.tradeveil

import java.text.SimpleDateFormat
import java.util.*

data class Transfer(
    val senderId: String = "",
    val senderEmail: String = "",
    val receiverId: String = "",
    val receiverEmail: String = "",
    val points: Long = 0,
    val fee: Long = 0,
    val timestamp: Long = 0,
    val status: String = "",
    val isTransferPoints: Boolean = true,
    val transactionId: String = ""
) {
    fun getFormattedDate(): String {
        return try {
            // CHANGED: Updated to show time in 24-hour format with date and month only
            val sdf = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    fun getFormattedPoints(): String {
        return "%,d".format(points)
    }

    fun getTotalCost(): Long {
        return points + fee
    }

    fun getFormattedTotalCost(): String {
        return "%,d".format(getTotalCost())
    }

    fun isValid(): Boolean {
        return senderId.isNotEmpty() &&
                receiverId.isNotEmpty() &&
                points > 0 &&
                timestamp > 0 &&
                status == "completed"
    }

    companion object {
        // CHANGED: Updated companion object date format to match
        private val dateFormat = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault())
    }
}