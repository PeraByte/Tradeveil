package com.koasac.tradeveil

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class Settings : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var notificationWorkManager: NotificationWorkManager

    companion object {
        private const val PREFS_NAME = "NotificationPrefs"
        private const val KEY_MESSAGES = "messages_enabled"
        private const val KEY_QUIZ = "quiz_reminders_enabled"
        private const val KEY_UPDATES = "update_reminders_enabled"
        private const val DEFAULT_MESSAGES = true
        private const val DEFAULT_QUIZ = true
        private const val DEFAULT_UPDATES = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        notificationWorkManager = NotificationWorkManager(this)

        // Initialize views
        val backButton = findViewById<ImageView>(R.id.backButton)
        val editProfileLayout = findViewById<LinearLayout>(R.id.editProfileLayout)
        val changeEmailLayout = findViewById<LinearLayout>(R.id.changeEmailLayout)
        val changePasswordLayout = findViewById<LinearLayout>(R.id.changePasswordLayout)
        val messagesSwitch = findViewById<Switch>(R.id.messagesSwitch)
        val quizRemindersSwitch = findViewById<Switch>(R.id.quizRemindersSwitch)
        val updateRemindersSwitch = findViewById<Switch>(R.id.updateRemindersSwitch)
        val aboutLayout = findViewById<LinearLayout>(R.id.aboutLayout)
        val rateUsLayout = findViewById<LinearLayout>(R.id.rateUsLayout)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // Load saved preferences or use defaults if not set
        messagesSwitch.isChecked = sharedPreferences.getBoolean(KEY_MESSAGES, DEFAULT_MESSAGES)
        quizRemindersSwitch.isChecked = sharedPreferences.getBoolean(KEY_QUIZ, DEFAULT_QUIZ)
        updateRemindersSwitch.isChecked = sharedPreferences.getBoolean(KEY_UPDATES, DEFAULT_UPDATES)

        // Set click listeners
        backButton.setOnClickListener { finish() }
        editProfileLayout.setOnClickListener { startActivity(Intent(this, EditProfile::class.java)) }
        changeEmailLayout.setOnClickListener { startActivity(Intent(this, EditProfile::class.java)) }
        changePasswordLayout.setOnClickListener { startActivity(Intent(this, EditProfile::class.java)) }
        aboutLayout.setOnClickListener { openWebsite("https://www.tradeveil.com") }
        rateUsLayout.setOnClickListener { openWebsite("https://www.tradeveil.com") }
        logoutButton.setOnClickListener { logoutUser() }

        // Initialize notifications based on default/saved preferences
        initializeNotifications()

        // Set switch change listeners
        messagesSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_MESSAGES, isChecked).apply()
            handleMessagesNotificationChange(isChecked)
        }

        quizRemindersSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_QUIZ, isChecked).apply()
            handleQuizNotificationChange(isChecked)
        }

        updateRemindersSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_UPDATES, isChecked).apply()
            handleUpdateNotificationChange(isChecked)
        }
    }

    private fun initializeNotifications() {
        val username = FirebaseAuth.getInstance().currentUser?.displayName

        if (sharedPreferences.getBoolean(KEY_MESSAGES, DEFAULT_MESSAGES) ||
            sharedPreferences.getBoolean(KEY_QUIZ, DEFAULT_QUIZ)) {
            notificationWorkManager.scheduleRemindersIfNotScheduled(username)
        }
    }


    private fun handleMessagesNotificationChange(isEnabled: Boolean) {
        if (isEnabled) {
            notificationWorkManager.scheduleMessageReminder()
            Toast.makeText(this, "Message notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            notificationWorkManager.cancelMessageReminders()
            Toast.makeText(this, "Message notifications disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleQuizNotificationChange(isEnabled: Boolean) {
        if (isEnabled) {
            notificationWorkManager.scheduleQuizReminders()
            Toast.makeText(this, "Quiz reminders enabled", Toast.LENGTH_SHORT).show()
        } else {
            notificationWorkManager.cancelQuizReminders()
            Toast.makeText(this, "Quiz reminders disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleUpdateNotificationChange(isEnabled: Boolean) {
        try {
            if (isEnabled) {
                Toast.makeText(this, "Update notifications enabled", Toast.LENGTH_SHORT).show()
                // Optionally reschedule any pending update notifications here if needed
            } else {
                notificationWorkManager.cancelUpdateReminders()
                Toast.makeText(this, "Update notifications disabled", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error changing update notifications", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebsite(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open website", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rateApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open app store", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logoutUser() {
        notificationWorkManager.cancelAllNotifications()
        auth.signOut()

        // Reset to default notification preferences on logout
        with(sharedPreferences.edit()) {
            putBoolean(KEY_MESSAGES, DEFAULT_MESSAGES)
            putBoolean(KEY_QUIZ, DEFAULT_QUIZ)
            putBoolean(KEY_UPDATES, DEFAULT_UPDATES)
            apply()
        }

        val intent = Intent(this, Login::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}