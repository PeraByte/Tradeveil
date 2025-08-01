package com.example.tradeveil

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.example.tradeveil.services.AdManager
import com.example.tradeveil.services.TransferService
import com.example.tradeveil.services.com.example.tradeveil.NotificationHelper
import com.example.tradeveil.NotificationWorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationWorkManager: NotificationWorkManager

    override fun onCreate() {
        super.onCreate()

        // Initialize AdManager (existing code)
        AdManager.initialize(this)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Initialize Notification WorkManager
        notificationWorkManager = NotificationWorkManager(this)

        // Initialize notification channels
        NotificationHelper(this)

        // Setup notification auth listener
        setupNotificationAuthListener()

        // Add this single line
        com.example.tradeveil.utils.NetworkMonitor.init(this)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun setupNotificationAuthListener() {
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                // User is signed in - schedule notifications and start transfer service
                notificationWorkManager.apply {
                    cancelAllNotifications()
                    scheduleRemindersIfNotScheduled(auth.currentUser?.displayName)
                }

                // Start transfer service for real-time transfer notifications
                startTransferService()
            } else {
                // User signed out - cancel all notifications and stop transfer service
                notificationWorkManager.cancelAllNotifications()
                stopTransferService()
            }
        }
    }

    private fun startTransferService() {
        try {
            val serviceIntent = Intent(this, TransferService::class.java)
            // FIXED: Use startService() instead of startForegroundService()
            // since we removed the foreground notification from TransferService
            startService(serviceIntent)
        } catch (e: Exception) {
            // Handle potential SecurityException
            android.util.Log.e("MyApplication", "Failed to start TransferService", e)
        }
    }

    private fun stopTransferService() {
        try {
            val serviceIntent = Intent(this, TransferService::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            android.util.Log.e("MyApplication", "Failed to stop TransferService", e)
        }
    }

    // Helper function to manually trigger update notifications
    fun showUpdateNotification(version: String) {
        NotificationHelper(this).showNotification(
            channelId = NotificationHelper.CHANNEL_ID_UPDATES,
            title = "New Update Available!",
            message = "Version $version is ready to download with new features!",
            username = auth.currentUser?.displayName
        )
    }
}