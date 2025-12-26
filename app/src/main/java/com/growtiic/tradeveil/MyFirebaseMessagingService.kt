package com.growtiic.tradeveil

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.growtiic.tradeveil.services.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val notificationHelper by lazy { NotificationHelper(this) }
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.notification?.let { notification ->
            val channelId = remoteMessage.data["channel_id"] ?: NotificationHelper.CHANNEL_ID_UPDATES
            val username = remoteMessage.data["username"]
            val type = remoteMessage.data["type"] ?: "update"

            // Show notification
            notificationHelper.showNotification(
                channelId = channelId,
                title = notification.title ?: "New Notification",
                message = notification.body ?: "You have a new notification",
                username = username
            )

            // Store in Firestore
            storeNotification(
                title = notification.title ?: "New Notification",
                body = notification.body ?: "",
                type = type
            )
        }
    }

    private fun storeNotification(title: String, body: String, type: String) {
        val userId = auth.currentUser?.uid ?: return

        val notification = Notification(
            title = title,
            body = body,
            timestamp = Date(),
            type = type
        )

        db.collection("users").document(userId).collection("notifications")
            .add(notification)
            .addOnSuccessListener {  }
            .addOnFailureListener { e ->  }
    }

    private fun sendTokenToServer(token: String) {
        // Implement your server token update logic here
    }
}