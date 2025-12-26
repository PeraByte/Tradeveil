// Enhanced NotificationWorkManager.kt - Removed transfer-specific methods since TransferService handles them
package com.growtiic.tradeveil

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.growtiic.tradeveil.services.NotificationHelper
import java.util.concurrent.TimeUnit

class NotificationWorkManager(private val context: Context) {

    companion object {
        private const val TAG = "NotificationWorkManager"
        private const val MESSAGE_WORKER_TAG = "message_reminder"
        private const val QUIZ_WORKER_TAG = "quiz_reminder"
        private const val UPDATE_WORKER_TAG = "update_reminder"
        // Removed TRANSFER_WORKER_TAG since TransferService handles transfer notifications
    }

    // Message reminder (repeats every 24 hours)
    fun scheduleMessageReminder(username: String? = null) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                24, TimeUnit.HOURS
            ).setInitialDelay(24, TimeUnit.HOURS)
                .setInputData(
                    workDataOf(
                        "channel_id" to NotificationHelper.CHANNEL_ID_MESSAGES,
                        "title" to "New messages waiting!",
                        "message" to "Check out what's new in the global chat!",
                        "username" to username
                    )
                ).addTag(MESSAGE_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                MESSAGE_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
        }
    }

    // Quiz reminders (repeats every 24 hours)
    fun scheduleQuizReminders(username: String? = null) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                24, TimeUnit.HOURS
            ).setInitialDelay(12, TimeUnit.HOURS)
                .setInputData(
                    workDataOf(
                        "channel_id" to NotificationHelper.CHANNEL_ID_QUIZ,
                        "title" to "Quiz time!",
                        "message" to "New quizzes are available!",
                        "username" to username
                    )
                ).addTag(QUIZ_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                QUIZ_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
        }
    }

    // For immediate update notifications
    fun scheduleUpdateNotification(version: String) {
        try {
            val updateWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(
                    workDataOf(
                        "channel_id" to NotificationHelper.CHANNEL_ID_UPDATES,
                        "title" to "New Update Available!",
                        "message" to "Version $version is ready with new features!"
                    )
                ).addTag(UPDATE_WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueue(updateWorkRequest)
        } catch (e: Exception) {
        }
    }

    fun scheduleRemindersIfNotScheduled(username: String? = null) {
        val workManager = WorkManager.getInstance(context)

        try {
            // Check message reminders
            val messageWorks = workManager.getWorkInfosByTag(MESSAGE_WORKER_TAG).get()
            val hasActiveMessageWork = messageWorks.any { workInfo ->
                workInfo.state == androidx.work.WorkInfo.State.ENQUEUED ||
                        workInfo.state == androidx.work.WorkInfo.State.RUNNING
            }

            if (!hasActiveMessageWork) {
                scheduleMessageReminder(username)
            }

            // Check quiz reminders
            val quizWorks = workManager.getWorkInfosByTag(QUIZ_WORKER_TAG).get()
            val hasActiveQuizWork = quizWorks.any { workInfo ->
                workInfo.state == androidx.work.WorkInfo.State.ENQUEUED ||
                        workInfo.state == androidx.work.WorkInfo.State.RUNNING
            }

            if (!hasActiveQuizWork) {
                scheduleQuizReminders(username)
            }
        } catch (e: Exception) {
        }
    }

    // Get status of scheduled notifications
    fun getNotificationStatus(): String {
        return try {
            val workManager = WorkManager.getInstance(context)
            val messageWorks = workManager.getWorkInfosByTag(MESSAGE_WORKER_TAG).get()
            val quizWorks = workManager.getWorkInfosByTag(QUIZ_WORKER_TAG).get()

            val messageStatus = messageWorks.firstOrNull()?.state?.name ?: "Not scheduled"
            val quizStatus = quizWorks.firstOrNull()?.state?.name ?: "Not scheduled"

            "Messages: $messageStatus, Quiz: $quizStatus"
        } catch (e: Exception) {
            "Unable to get status"
        }
    }

    // Cancellation methods
    fun cancelMessageReminders() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(MESSAGE_WORKER_TAG)
        } catch (e: Exception) {
        }
    }

    fun cancelQuizReminders() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(QUIZ_WORKER_TAG)
        } catch (e: Exception) {
        }
    }

    fun cancelUpdateReminders() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag(UPDATE_WORKER_TAG)
        } catch (e: Exception) {
        }
    }

    fun cancelAllNotifications() {
        try {
            WorkManager.getInstance(context).apply {
                cancelAllWorkByTag(MESSAGE_WORKER_TAG)
                cancelAllWorkByTag(QUIZ_WORKER_TAG)
                cancelAllWorkByTag(UPDATE_WORKER_TAG)
                // Note: TransferService handles its own lifecycle, no need to cancel transfer notifications here
            }
        } catch (e: Exception) {
        }
    }
}