package com.growtic.tradeveil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.growtic.tradeveil.services.AdManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class Missions : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Social media URLs
    private val instagramUrl = "https://www.instagram.com/tradeveil/"
    private val twitterUrl = "https://x.com/TradeVeil"
    private val youtubeUrl = "https://www.youtube.com/@TradeVeil"
    private val facebookUrl = "https://www.facebook.com/profile.php?id=61577340760736"
    private val telegramUrl = "https://t.me/tradeveil"
    private val partnerWebsiteUrl = "https://tradeveil.com"

    companion object {
        private const val TAG = "Missions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_missions)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize AdManager
        AdManager.initialize(this)

        // Add logging to verify onCreate is called

        // Initialize buttons and set click listeners
        initializeButtons()

        // Partner mission button
        findViewById<TextView>(R.id.visitWebsiteButton)?.setOnClickListener {
            showAdBeforeOpeningWebsite()
        }
    }

    private fun initializeButtons() {
        try {
            setupSocialTaskButton(R.id.instagramButton, "instagram", instagramUrl)
            setupSocialTaskButton(R.id.twitterButton, "twitter", twitterUrl)
            setupSocialTaskButton(R.id.youtubeButton, "youtube", youtubeUrl)
            setupSocialTaskButton(R.id.facebookButton, "facebook", facebookUrl)
            setupSocialTaskButton(R.id.telegramButton, "telegram", telegramUrl)
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading tasks. Please restart the app.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSocialTaskButton(buttonId: Int, platform: String, url: String) {
        val button = findViewById<Button>(buttonId)

        if (button == null) {
            return
        }

        // Ensure button has initial text if not set in XML
        if (button.text.isNullOrEmpty()) {
            button.text = "Start"
        }

        // Check task status on startup
        checkTaskCompletionStatus(platform, button)

        // Set click listener with error handling
        button.setOnClickListener {
            try {
                handleSocialTask(platform, url, button)
            } catch (e: Exception) {
                Toast.makeText(this, "Error processing task. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkTaskCompletionStatus(platform: String, button: Button) {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val completedTasks = userDoc.get("completedTasks") as? List<String> ?: listOf()

                if (completedTasks.contains(platform)) {
                    withContext(Dispatchers.Main) {
                        markTaskAsCompleted(button)
                    }
                }
            } catch (e: Exception) {
                // Silent fail - we'll check again when user clicks
            }
        }
    }

    private fun showAdBeforeOpeningWebsite() {
        AdManager.showMissionsRewardAd(
            activity = this,
            onAdDismissed = {
                // Ad was dismissed without completing - still open website but show message
                openTradeVeilWebsite()
            },
            onRewardEarned = {
                // Ad was watched completely - open website
                openTradeVeilWebsite()
            },
            onAdFailedToLoad = {
                // Ad failed to load - open website anyway
                openTradeVeilWebsite()
            }
        )
    }

    private fun openTradeVeilWebsite() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(partnerWebsiteUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open website. Please try again later.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSocialTask(platform: String, url: String, button: Button) {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to complete tasks", Toast.LENGTH_SHORT).show()
            return
        }

        val currentText = button.text.toString().trim()

        when (currentText) {
            "Start Task" -> {
                button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_btn)

                try {
                    // Open social profile - just launch it directly
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)

                    // Change to Verify button
                    button.text = "Verify"
                    button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.black)
                    button.setTextColor(ContextCompat.getColor(this, R.color.white))

                } catch (e: Exception) {
                    Toast.makeText(this, "Error opening link. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            "Verify" -> {
                // Show rewarded ad before starting verification process
                showRewardedAdAndStartVerification(button)
            }

            "Claim" -> {
                // Reward points and mark as completed
                completeSocialTask(platform, button, currentUser.uid)
            }

            else -> {
                // Reset button to start state
                button.text = "Start"
                button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_app)
                button.setTextColor(ContextCompat.getColor(this, R.color.white))
                button.isEnabled = true
            }
        }
    }

    private fun showRewardedAdAndStartVerification(button: Button) {
        // Show missions rewarded ad with callback to start verification after ad is completed
        AdManager.showMissionsRewardAd(
            activity = this,
            onAdDismissed = {
                // This runs if the ad is dismissed without watching it completely
                Toast.makeText(this, "Please watch the complete ad to verify your task", Toast.LENGTH_SHORT).show()
            },
            onRewardEarned = {
                // This runs after the ad is watched completely (reward earned)
                startVerificationProcess(button)
            },
            onAdFailedToLoad = {
                // This runs if the ad fails to load
                Toast.makeText(this, "Ad not available. Starting verification anyway.", Toast.LENGTH_SHORT).show()
                startVerificationProcess(button)
            }
        )
    }

    private fun startVerificationProcess(button: Button) {
        button.isEnabled = false
        button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nav_grey)
        button.setTextColor(ContextCompat.getColor(this, R.color.black))

        // Start 30 second timer and update text every second
        object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                button.text = "Wait ($secondsLeft)"
            }

            override fun onFinish() {
                // Activate claim state
                button.text = "Claim"
                button.isEnabled = true
                button.backgroundTintList = ContextCompat.getColorStateList(this@Missions, R.color.green)
                button.setTextColor(ContextCompat.getColor(this@Missions, R.color.white))
            }
        }.start()
    }

    private fun completeSocialTask(platform: String, button: Button, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check points limit before updating (to comply with Firestore rules)
                val userDoc = db.collection("users").document(userId).get().await()
                val currentPoints = userDoc.getLong("points") ?: 0

                // Ensure we don't exceed the 1,000,000 points limit
                if (currentPoints + 100 > 1000000) {
                    withContext(Dispatchers.Main) {
                        button.text = "Start"
                        button.backgroundTintList = ContextCompat.getColorStateList(this@Missions, R.color.blue_app)
                        button.setTextColor(ContextCompat.getColor(this@Missions, R.color.white))
                        button.isEnabled = true
                        Toast.makeText(this@Missions, "Points limit reached! Cannot complete task.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Update user's points and completed tasks
                db.collection("users").document(userId).update(
                    mapOf(
                        "points" to FieldValue.increment(100),
                        "completedTasks" to FieldValue.arrayUnion(platform)
                    )
                ).await()

                withContext(Dispatchers.Main) {
                    markTaskAsCompleted(button)
                    Toast.makeText(this@Missions, "+100 points added to your wallet!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    button.text = "Start"
                    button.backgroundTintList = ContextCompat.getColorStateList(this@Missions, R.color.blue_app)
                    button.setTextColor(ContextCompat.getColor(this@Missions, R.color.white))
                    button.isEnabled = true
                    Toast.makeText(this@Missions, "Failed to complete task: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun markTaskAsCompleted(button: Button) {
        button.text = "Completed"
        button.setTextColor(ContextCompat.getColor(this, R.color.white))
        button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.nav_grey)
        button.isEnabled = false
    }
}