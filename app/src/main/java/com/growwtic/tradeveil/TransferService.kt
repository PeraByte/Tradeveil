package com.growwtic.tradeveil.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.growwtic.tradeveil.Notification
import com.growwtic.tradeveil.Transfer
import com.growwtic.tradeveil.services.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*

class TransferService : Service() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationHelper: NotificationHelper
    private var transferListener: ListenerRegistration? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processedTransfers = mutableSetOf<String>() // Prevent duplicate notifications

    companion object {
        private const val TAG = "TransferService"
        private const val PREFS_NAME = "transfer_notifications"
        private const val KEY_PROCESSED_TRANSFERS = "processed_transfers"
    }

    override fun onCreate() {
        super.onCreate()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        notificationHelper = NotificationHelper(this)

        // Load previously processed transfers to avoid duplicates
        loadProcessedTransfers()

        // Start listening for transfers (NO FOREGROUND SERVICE)
        startListeningForTransfers()
    }

    // REMOVED: startForegroundService() method - this was causing the persistent notification

    private fun loadProcessedTransfers() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val processedSet = prefs.getStringSet(KEY_PROCESSED_TRANSFERS, emptySet()) ?: emptySet()
        processedTransfers.addAll(processedSet)
    }

    private fun saveProcessedTransfer(transferId: String) {
        processedTransfers.add(transferId)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_PROCESSED_TRANSFERS, processedTransfers)
            .apply()
    }

    private fun startListeningForTransfers() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            stopSelf()
            return
        }

        transferListener = db.collection("transfers")
            .whereEqualTo("receiverId", uid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val transferId = change.document.id
                    val transfer = change.document.toObject(Transfer::class.java)

                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            // Only process if it's a completed transfer and not already processed
                            if (transfer.status == "completed" && !processedTransfers.contains(transferId)) {
                                serviceScope.launch {
                                    handleNewTransfer(transfer, transferId)
                                }
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            // Handle status changes (e.g., pending -> completed)
                            if (transfer.status == "completed" && !processedTransfers.contains(transferId)) {
                                serviceScope.launch {
                                    handleNewTransfer(transfer, transferId)
                                }
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            // Handle if needed
                        }
                    }
                }
            }
    }

    private suspend fun handleNewTransfer(transfer: Transfer, transferId: String) {
        try {
            // Double-check to prevent race conditions
            if (processedTransfers.contains(transferId)) {
                return
            }

            // Get sender's username for better UX
            val senderDoc = db.collection("users")
                .document(transfer.senderId)
                .get()
                .await()

            val senderUsername = senderDoc.getString("username")
                ?: transfer.senderEmail.substringBefore("@")

            val formattedPoints = "%,d".format(transfer.points)
            val title = "Money Received"
            val message = "$senderUsername sent you $formattedPoints points🎉"

            // Show system notification
            withContext(Dispatchers.Main) {
                notificationHelper.showTransferNotification(title, message, isReceived = true)
            }

            // Save to user's notification collection
            saveTransferNotificationToFirestore(title, message, "transfer_received")

            // Mark as processed to prevent duplicates
            saveProcessedTransfer(transferId)

        } catch (e: Exception) {
            // Handle exception silently
        }
    }

    private suspend fun saveTransferNotificationToFirestore(title: String, message: String, type: String) {
        try {
            val uid = auth.currentUser?.uid ?: return

            val notification = Notification(
                title = title,
                body = message,
                timestamp = Date(),
                type = type
            )

            db.collection("users")
                .document(uid)
                .collection("notifications")
                .add(notification)
                .await()

        } catch (e: Exception) {
            // Handle exception silently
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Service will be restarted if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        transferListener?.remove()
        serviceScope.cancel()
    }
}