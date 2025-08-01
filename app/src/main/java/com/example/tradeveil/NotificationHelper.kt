package com.example.tradeveil.services.com.example.tradeveil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.tradeveil.MainActivity
import com.example.tradeveil.ManagePoints
import com.example.tradeveil.R

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID_MESSAGES = "channel_messages"
        const val CHANNEL_ID_QUIZ = "channel_quiz"
        const val CHANNEL_ID_UPDATES = "channel_updates"
        const val CHANNEL_ID_TRANSFERS = "channel_transfers" // New channel for transfers
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ID_MESSAGES,
                    "Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for new messages in global chat"
                },
                NotificationChannel(
                    CHANNEL_ID_QUIZ,
                    "Quiz Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders for available quizzes"
                },
                NotificationChannel(
                    CHANNEL_ID_UPDATES,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about app updates"
                },
                NotificationChannel(
                    CHANNEL_ID_TRANSFERS,
                    "Points Transfers",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for points transfers"
                    enableVibration(true)
                    setShowBadge(true)
                }
            )

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannels(channels)
        }
    }

    fun showNotification(channelId: String, title: String, message: String, username: String? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        username?.let {
            notificationBuilder.setSubText("Hi $username!")
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    // New method for transfer notifications with specific intent
    fun showTransferNotification(title: String, message: String, isReceived: Boolean = true) {
        val intent = Intent(context, ManagePoints::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isReceived) R.drawable.ic_transfer_received else R.drawable.ic_transfer_sent
        val color = if (isReceived) 0xFF4CAF50.toInt() else 0xFF2196F3.toInt()

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_TRANSFERS)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(color)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
