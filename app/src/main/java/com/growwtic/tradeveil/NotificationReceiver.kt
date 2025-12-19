package com.growwtic.tradeveil

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.growwtic.tradeveil.services.NotificationHelper

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = intent.getStringExtra("channel_id") ?: return
        val title = intent.getStringExtra("title") ?: return
        val message = intent.getStringExtra("message") ?: return
        val username = intent.getStringExtra("username")

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification(channelId, title, message, username)

        // No need to reschedule - WorkManager handles recurring notifications
    }
}
