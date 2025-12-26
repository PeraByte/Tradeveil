package com.growtiic.tradeveil

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.BounceInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import cn.pedant.SweetAlert.SweetAlertDialog
import com.growtiic.tradeveil.services.AdManager
import com.growtiic.tradeveil.viewmodels.LeaderboardViewModel
import com.growtiic.tradeveil.services.com.example.tradeveil.utils.QuizConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class StartQuiz : AppCompatActivity() {

    private lateinit var tvRankValue: TextView
    private lateinit var tvPointsValue: TextView
    private lateinit var btnStartQuiz: Button
    private lateinit var btnTotalPoints: Button
    private lateinit var tvQuizCountSuffix: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewModel: LeaderboardViewModel
    private var completedQuizzes = 0
    private var totalQuizzes = QuizConstants.TOTAL_QUIZZES
    private var lastQuizAttempt: Date? = null
    private var nextQuizAvailable: Date? = null
    private var hasClaimedReward = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_quiz)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        viewModel = ViewModelProvider(this)[LeaderboardViewModel::class.java]

        // Initialize AdManager
        AdManager.initialize(this)

        initViews()
        loadUserData()
        setupClickListeners()
    }

    private fun initViews() {
        tvRankValue = findViewById(R.id.tvRankValue)
        tvPointsValue = findViewById(R.id.tvPointsValue)
        btnStartQuiz = findViewById(R.id.btnStartQuiz)
        btnTotalPoints = findViewById(R.id.btnTotalPoints)
        tvQuizCountSuffix = findViewById(R.id.tvQuizCountSuffix)
        btnTotalPoints.isEnabled = false
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Load leaderboard data
            viewModel.leaderboardUsers.observe(this) { users ->
                users?.let {
                    val user = users.find { it.id == currentUser.uid }
                    user?.let {
                        tvRankValue.text = user.rank?.toString() ?: "-"
                        tvPointsValue.text = user.points.toString()
                    }
                }
            }

            // Load quiz progress data
            db.collection("users").document(currentUser.uid)
                .collection("quizProgress")
                .document("stats")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Load completed count
                        completedQuizzes = document.getLong("completed")?.toInt() ?: 0
                        tvQuizCountSuffix.text = "$completedQuizzes/$totalQuizzes"

                        // Load last attempt timestamp
                        val timestamp = document.getDate("lastAttempt")
                        lastQuizAttempt = timestamp
                        nextQuizAvailable = timestamp?.let {
                            val calendar = Calendar.getInstance()
                            calendar.time = it
                            calendar.add(Calendar.HOUR_OF_DAY, 24)
                            calendar.time
                        }

                        // Check if reward has been claimed
                        hasClaimedReward = document.getBoolean("rewardClaimed") ?: false

                        // Update UI based on completion status
                        updateUIBasedOnProgress()
                    } else {
                        // Create initial document if it doesn't exist
                        val initialData = hashMapOf(
                            "completed" to 0,
                            "rewardClaimed" to false
                        )
                        document.reference.set(initialData)
                        tvQuizCountSuffix.text = "0/$totalQuizzes"
                    }
                }
                .addOnFailureListener {
                    // Handle error
                    tvQuizCountSuffix.text = "0/$totalQuizzes"
                }

            viewModel.loadLeaderboard()
        }
    }

    private fun updateUIBasedOnProgress() {
        if (completedQuizzes >= totalQuizzes) {
            if (!hasClaimedReward) {
                btnTotalPoints.isEnabled = true
                btnTotalPoints.setBackgroundColor(getColor(R.color.green))
                btnTotalPoints.text = "Claim Reward (${QuizConstants.COMPLETION_BONUS} points)"
            } else {
                btnTotalPoints.setBackgroundColor(getColor(R.color.dark_green))
                btnTotalPoints.text = "Reward Claimed"
                btnTotalPoints.isEnabled = false
            }
        }
    }

    private fun setupClickListeners() {
        btnStartQuiz.setOnClickListener {
            // Show quiz interstitial ad before starting quiz
            if (AdManager.isQuizInterstitialAdReady()) {
                AdManager.showInterstitialAd(this) {
                    checkQuizAvailability()
                }
            } else {
                // If ad is not ready, proceed without ad
                checkQuizAvailability()
            }
        }

        btnTotalPoints.setOnClickListener {
            if (completedQuizzes >= totalQuizzes && !hasClaimedReward) {
                claimCompletionReward()
            }
        }

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
        }
    }

    private fun checkQuizAvailability() {
        val now = Date()

        // Check if user has completed all quizzes
        if (completedQuizzes >= totalQuizzes) {
            SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("All Quizzes Completed!")
                .setContentText("You have completed all $totalQuizzes quizzes. Great job!")
                .setConfirmText("OK")
                .show()
            return
        }

        // Check 24-hour cooldown
        nextQuizAvailable?.let { availableTime ->
            if (now.before(availableTime)) {
                showCooldownDialog(now, availableTime)
                return
            }
        }

        // Quiz is available, start it
        startQuiz()
    }

    private fun showCooldownDialog(now: Date, availableTime: Date) {
        val timeLeft = (availableTime.time - now.time) / 1000
        val hours = timeLeft / 3600
        val minutes = (timeLeft % 3600) / 60
        val seconds = timeLeft % 60

        SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
            .setTitleText("Quiz Cooldown")
            .setContentText("Please wait ${hours}h ${minutes}m ${seconds}s to take the next quiz")
            .setConfirmText("OK")
            .show()
    }

    private fun startQuiz() {
        val intent = Intent(this, Quiz::class.java)
        startActivity(intent)
    }

    private fun claimCompletionReward() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            // Update wallet balance
            db.collection("wallets").document(user.uid)
                .update("balance", FieldValue.increment(QuizConstants.COMPLETION_BONUS.toDouble()))
                .addOnSuccessListener {
                    // Update user points
                    db.collection("users").document(user.uid)
                        .update("points", FieldValue.increment(QuizConstants.COMPLETION_BONUS.toLong()))
                        .addOnSuccessListener {
                            // Mark reward as claimed
                            db.collection("users").document(user.uid)
                                .collection("quizProgress")
                                .document("stats")
                                .update("rewardClaimed", true)
                                .addOnSuccessListener {
                                    // Update UI
                                    hasClaimedReward = true
                                    btnTotalPoints.setBackgroundColor(getColor(R.color.dark_green))
                                    btnTotalPoints.text = "Reward Claimed"
                                    btnTotalPoints.isEnabled = false

                                    // Show animation
                                    val ivGiftBox = findViewById<ImageView>(R.id.ivGiftBox)
                                    val bounceAnim = ObjectAnimator.ofFloat(ivGiftBox, "translationY", 0f, -50f, 0f)
                                    bounceAnim.duration = 500
                                    bounceAnim.interpolator = BounceInterpolator()
                                    bounceAnim.start()

                                    // Show success dialog
                                    SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                                        .setTitleText("Reward Claimed!")
                                        .setContentText("You received ${QuizConstants.COMPLETION_BONUS} points!")
                                        .setConfirmText("Great!")
                                        .show()
                                }
                        }
                }
                .addOnFailureListener {
                    SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
                        .setTitleText("Error")
                        .setContentText("Failed to claim reward. Please try again.")
                        .setConfirmText("OK")
                        .show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}