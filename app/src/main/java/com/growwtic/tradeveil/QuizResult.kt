package com.growwtic.tradeveil

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.growwtic.tradeveil.services.AdManager
import com.growwtic.tradeveil.services.com.example.tradeveil.utils.QuizConstants

class QuizResult : AppCompatActivity() {

    private var pointsAnimator: ValueAnimator? = null
    private var animationView: LottieAnimationView? = null
    private var isDestroyed = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "QuizResult"
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_quiz_result)
            Log.d(TAG, "Layout set successfully")

            // Initialize AdManager
            AdManager.initialize(this)

            // Get intent data with null safety and logging
            val pointsEarned = intent?.getIntExtra("points", 0) ?: 0
            val attempts = intent?.getIntExtra("attempts", 1) ?: 1
            val totalCompleted = intent?.getIntExtra("totalCompleted", 0) ?: 0
            val isCorrect = intent?.getBooleanExtra("isCorrect", false) ?: false

            Log.d(TAG, "Intent data - Points: $pointsEarned, Attempts: $attempts, Total: $totalCompleted, Correct: $isCorrect")

            setupViews(pointsEarned, attempts, totalCompleted, isCorrect)

        } catch (e: Exception) {
            // Graceful fallback
            finish()
        }
    }

    private fun setupViews(pointsEarned: Int, attempts: Int, totalCompleted: Int, isCorrect: Boolean) {
        try {
            Log.d(TAG, "Setting up views")

            // Find views with null checks and logging
            val tvPoints = findViewById<TextView>(R.id.tvScore)
            val tvCongrats = findViewById<TextView>(R.id.tvCongrats)
            val tvAttemptInfo = findViewById<TextView>(R.id.tvAttemptInfo)
            val tvProgressInfo = findViewById<TextView>(R.id.tvProgressInfo)
            animationView = findViewById<LottieAnimationView>(R.id.animationView)
            val btnFinish = findViewById<Button>(R.id.btnFinish)

            // Check if critical views were found
            if (tvPoints == null) Log.e(TAG, "tvScore not found")
            if (tvCongrats == null) Log.e(TAG, "tvCongrats not found")
            if (animationView == null) Log.e(TAG, "animationView not found")

            // Set initial points display
            tvPoints?.text = "+0 points"

            // Set congratulation message based on performance
            tvCongrats?.text = getCongratsMessage(pointsEarned, attempts, isCorrect)

            // Show attempt info with better logic
            tvAttemptInfo?.text = getAttemptMessage(pointsEarned, attempts, isCorrect)

            // Show progress info with null safety and QuizConstants check
            val totalQuizzes = try {
                QuizConstants.TOTAL_QUIZZES
            } catch (e: Exception) {
                50 // fallback value
            }
            tvProgressInfo?.text = "Quiz Progress: $totalCompleted/$totalQuizzes"

            // Setup animation with error handling - delay this to avoid immediate crashes
            handler.postDelayed({
                if (!isDestroyed) {
                    setupLottieAnimation(pointsEarned, isCorrect)
                }
            }, 500)

            // Animate points counting with lifecycle safety - delay this too
            handler.postDelayed({
                if (!isDestroyed) {
                    animatePointsCounting(tvPoints, pointsEarned)
                }
            }, 800)

            // Setup finish button with ad integration
            btnFinish?.setOnClickListener {
                // Show interstitial ad before finishing
                if (AdManager.isQuizInterstitialAdReady()) {
                    AdManager.showInterstitialAd(this) {
                        // Ad dismissed, finish activity
                        finish()
                    }
                } else {
                    // If ad is not ready, finish without ad
                    finish()
                }
            }

            Log.d(TAG, "Views setup completed")

        } catch (e: Exception) {
            finish()
        }
    }

    private fun animatePointsCounting(tvPoints: TextView?, pointsEarned: Int) {
        if (tvPoints == null || isDestroyed) {
            return
        }

        try {

            // Cancel any existing animator
            pointsAnimator?.cancel()

            if (pointsEarned <= 0) {
                tvPoints.text = "+0 points"
                return
            }

            pointsAnimator = ValueAnimator.ofInt(0, pointsEarned).apply {
                duration = 1500
                addUpdateListener { animator ->
                    if (!isDestroyed && tvPoints != null) {
                        try {
                            val value = animator.animatedValue as? Int ?: 0
                            tvPoints.text = "+$value points"
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating points animation", e)
                        }
                    }
                }
            }
            pointsAnimator?.start()

        } catch (e: Exception) {
            Log.e(TAG, "Error in animatePointsCounting", e)
            // Fallback to static display
            tvPoints.text = "+$pointsEarned points"
        }
    }

    private fun getAttemptMessage(pointsEarned: Int, attempts: Int, isCorrect: Boolean): String {
        return when {
            isCorrect && pointsEarned > 0 -> {
                "Completed in $attempts attempt${if (attempts > 1) "s" else ""}"
            }
            attempts >= getMaxAttempts() && pointsEarned == 0 -> {
                "All attempts used"
            }
            pointsEarned == 0 -> {
                "Time's up or incorrect answer"
            }
            else -> {
                "Completed in $attempts attempt${if (attempts > 1) "s" else ""}"
            }
        }
    }

    private fun getMaxAttempts(): Int {
        return try {
            QuizConstants.MAX_ATTEMPTS
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing QuizConstants.MAX_ATTEMPTS", e)
            3 // fallback value
        }
    }

    private fun getCongratsMessage(pointsEarned: Int, attempts: Int, isCorrect: Boolean): String {
        return when {
            pointsEarned == 100 -> "Perfect! First try! 🎉"
            pointsEarned == 50 -> "Good Job! Second attempt! 👍"
            pointsEarned == 25 -> "Not bad! Third attempt! 💪"
            pointsEarned == 0 -> "Better luck next time! 🍀"
            isCorrect -> "Great work! You got it right! ✨"
            else -> "Keep trying! You'll get it next time! 💪"
        }
    }

    private fun setupLottieAnimation(pointsEarned: Int, isCorrect: Boolean) {
        if (animationView == null || isDestroyed) {
            Log.d(TAG, "Skipping Lottie animation - view null or destroyed")
            return
        }

        try {
            Log.d(TAG, "Setting up Lottie animation")

            // Determine animation resource with fallback
            val animationResource = when {
                pointsEarned >= 100 -> R.raw.check_mark_anim
                pointsEarned >= 50 -> R.raw.thumbs_up
                pointsEarned >= 25 -> R.raw.thumbs_up
                isCorrect -> R.raw.thumbs_up
                else -> R.raw.try_again
            }

            Log.d(TAG, "Selected animation resource: $animationResource")

            // Set animation with error handling
            animationView?.let { animation ->
                try {
                    // Check if the raw resource exists
                    val resources = resources
                    val resourceName = resources.getResourceName(animationResource)
                    Log.d(TAG, "Using animation resource: $resourceName")

                    animation.setAnimation(animationResource)

                    // Add a small delay to prevent immediate animation issues
                    handler.postDelayed({
                        if (!isDestroyed && animation != null) {
                            try {
                                Log.d(TAG, "Playing Lottie animation")
                                animation.playAnimation()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error playing animation", e)
                                // Try fallback animation
                                playFallbackAnimation(animation)
                            }
                        }
                    }, 300)

                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up animation", e)
                    playFallbackAnimation(animation)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in setupLottieAnimation", e)
        }
    }

    private fun playFallbackAnimation(animation: LottieAnimationView) {
        try {
            Log.d(TAG, "Playing fallback animation")
            // Try to use a basic animation or hide the view
            animation.visibility = android.view.View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback animation", e)
            // Hide animation view if all else fails
            animation.visibility = android.view.View.GONE
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        isDestroyed = true

        // Cancel animations to prevent memory leaks
        pointsAnimator?.cancel()
        pointsAnimator = null

        // Stop Lottie animation
        animationView?.cancelAnimation()
        animationView = null

        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
        // Pause animations when activity is paused
        pointsAnimator?.pause()
        animationView?.pauseAnimation()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        // Resume animations when activity is resumed
        if (!isDestroyed) {
            pointsAnimator?.resume()
            animationView?.resumeAnimation()
        }
    }
}