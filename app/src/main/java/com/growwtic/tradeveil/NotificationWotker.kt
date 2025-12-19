package com.growwtic.tradeveil

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.growwtic.tradeveil.services.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "NotificationWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val channelId = inputData.getString("channel_id") ?: return@withContext Result.failure()
            val title = inputData.getString("title") ?: return@withContext Result.failure()
            val message = inputData.getString("message") ?: return@withContext Result.failure()
            val username = inputData.getString("username")


            // Show system notification with appropriate method
            val notificationHelper = NotificationHelper(applicationContext)

            if (channelId == NotificationHelper.CHANNEL_ID_TRANSFERS) {
                // Use transfer-specific notification for better UX
                notificationHelper.showTransferNotification(title, message)
            } else {
                // Use regular notification for other types
                notificationHelper.showNotification(channelId, title, message, username)
            }


            // Save to Firestore
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val notification = Notification(
                        title = title,
                        body = message,
                        timestamp = java.util.Date(),
                        type = when (channelId) {
                            NotificationHelper.CHANNEL_ID_MESSAGES -> "message"
                            NotificationHelper.CHANNEL_ID_QUIZ -> "quiz"
                            NotificationHelper.CHANNEL_ID_UPDATES -> "update"
                            NotificationHelper.CHANNEL_ID_TRANSFERS -> "transfer"
                            else -> "unknown"
                        }
                    )

                    db.collection("users")
                        .document(userId)
                        .collection("notifications")
                        .add(notification)
                        .addOnSuccessListener {
                        }
                        .addOnFailureListener { e ->
                        }
                } catch (e: Exception) {
                }
            } else {
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}