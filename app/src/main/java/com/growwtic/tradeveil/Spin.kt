package com.growwtic.tradeveil

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.random.Random

class Spin : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var spinButton: Button
    private lateinit var balanceTextView: TextView
    private lateinit var wheelImageView: ImageView
    private var isSpinning = false
    private var currentAnimator: ObjectAnimator? = null
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_spin)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        spinButton = findViewById(R.id.button20)
        balanceTextView = findViewById(R.id.textView68)
        wheelImageView = findViewById(R.id.imageView56)
        backButton = findViewById(R.id.backButton)

        // Load user balance and check spin availability
        loadUserBalance()
        checkSpinAvailability()

        spinButton.setOnClickListener {
            if (!isSpinning) {
                startSpin()
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up animations to prevent crashes
        currentAnimator?.cancel()
    }

    private fun loadUserBalance() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "Error loading balance", Toast.LENGTH_SHORT).show()
                        }
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val points = snapshot.getLong("points") ?: 0
                        if (!isFinishing && !isDestroyed) {
                            balanceTextView.text = points.toString()
                        }
                    }
                }
        }
    }

    private fun checkSpinAvailability() {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val lastSpinTime = userDoc.getLong("lastSpinTime") ?: 0L
                val currentTime = System.currentTimeMillis()
                val twentyFourHours = 24 * 60 * 60 * 1000L

                withContext(Dispatchers.Main) {
                    // Check if activity is still valid
                    if (isFinishing || isDestroyed) return@withContext

                    if (currentTime - lastSpinTime < twentyFourHours) {
                        // Calculate remaining time
                        val remainingTime = twentyFourHours - (currentTime - lastSpinTime)
                        val hoursLeft = remainingTime / (60 * 60 * 1000)
                        val minutesLeft = (remainingTime % (60 * 60 * 1000)) / (60 * 1000)

                        spinButton.isEnabled = false
                        spinButton.text = "Wait ${hoursLeft}h ${minutesLeft}m"

                        // Start countdown timer
                        startCountdownTimer(remainingTime)
                    } else {
                        spinButton.isEnabled = true
                        spinButton.text = "SPIN"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@Spin, "Error checking spin availability", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startCountdownTimer(remainingTime: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            var timeLeft = remainingTime
            while (timeLeft > 0 && !spinButton.isEnabled && !isFinishing && !isDestroyed) {
                kotlinx.coroutines.delay(1000)
                timeLeft -= 1000

                if (isFinishing || isDestroyed) break

                val hoursLeft = timeLeft / (60 * 60 * 1000)
                val minutesLeft = (timeLeft % (60 * 60 * 1000)) / (60 * 1000)
                val secondsLeft = (timeLeft % (60 * 1000)) / 1000

                spinButton.text = "Wait ${hoursLeft}hr"
            }

            if (timeLeft <= 0 && !isFinishing && !isDestroyed) {
                spinButton.isEnabled = true
                spinButton.text = "SPIN"
            }
        }
    }

    private fun startSpin() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val currentPoints = userDoc.getLong("points") ?: 0
                val lastSpinTime = userDoc.getLong("lastSpinTime") ?: 0L
                val currentTime = System.currentTimeMillis()
                val twentyFourHours = 24 * 60 * 60 * 1000L

                // Check 24-hour cooldown
                if (currentTime - lastSpinTime < twentyFourHours) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this@Spin, "You can spin once every 24 hours", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                // Check if user has enough points
                if (currentPoints < 10) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this@Spin, "Not enough points (need 10)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }

                // Deduct 10 points and update last spin time
                db.collection("users").document(currentUser.uid)
                    .update(mapOf(
                        "points" to currentPoints - 10,
                        "lastSpinTime" to currentTime
                    ))
                    .await()

                // Determine prize first (independent of wheel position)
                val prize = determinePrize()

                // Start the spin animation
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        spinWheel(prize.value)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@Spin, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun spinWheel(prizeValue: Int) {
        if (isSpinning || isFinishing || isDestroyed) return

        isSpinning = true
        spinButton.isEnabled = false

        // Get the current rotation of the wheel
        val currentRotation = wheelImageView.rotation

        // Add multiple rotations for spinning effect (5 seconds total)
        val baseRotations = Random.nextInt(10, 15) * 360f

        // Final rotation should return to original position (0 degrees)
        val finalRotation = currentRotation + baseRotations

        // Create spin animation that lasts 5 seconds
        currentAnimator = ObjectAnimator.ofFloat(wheelImageView, View.ROTATION, currentRotation, finalRotation)
        currentAnimator?.duration = 5000 // 5 seconds
        currentAnimator?.interpolator = AccelerateDecelerateInterpolator()

        // Set up animation end listener
        currentAnimator?.doOnEnd {
            if (!isFinishing && !isDestroyed) {
                // Reset wheel to original position (0 degrees)
                wheelImageView.rotation = 0f
                isSpinning = false
                // Don't re-enable button immediately - wait for 24 hours
                checkSpinAvailability()
            }
        }

        // Start the animation
        currentAnimator?.start()

        // Show prize dialog after 3.5 seconds (while wheel is still spinning)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                showPrizeDialog(prizeValue)
            }
        }, 3500) // 3.5 seconds
    }

    private fun determinePrize(): Prize {
        val random = Random.nextDouble(0.0, 100.0)
        return when {
            random < 5 -> Prize(60, 2)      // 5% chance - 60 veils - RARE
            random < 10 -> Prize(60, 6)     // 5% chance - 60 veils - RARE
            random < 20 -> Prize(30, 4)     // 10% chance - 30 veils - UNCOMMON
            random < 50 -> Prize(20, 7)     // 30% chance - 20 veils - COMMON
            else -> Prize(10, 1)            // 50% chance - 10 veils - MOST COMMON
        }
    }

    private fun showPrizeDialog(pointsWon: Int) {
        if (isFinishing || isDestroyed) return

        try {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_prize)

            // Make dialog background transparent
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Set dialog width and height
            val window = dialog.window
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 1).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Find all TextViews from the XML
            val prizeText = dialog.findViewById<TextView>(R.id.prizeText)
            val prizeText2 = dialog.findViewById<TextView>(R.id.prizeText2)
            val prizeText3 = dialog.findViewById<TextView>(R.id.prizeText3)
            val crossButton = dialog.findViewById<Button>(R.id.crossButton)
            val lottieAnimation = dialog.findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.bottomSheetAnimation)

            if (prizeText == null || crossButton == null) {
                Toast.makeText(this, "Dialog layout error", Toast.LENGTH_SHORT).show()
                addPointsToWallet(pointsWon)
                return
            }

            // Make all text views visible and set their content according to XML design
            prizeText3?.visibility = View.VISIBLE
            prizeText3?.text = "Congratulations!"

            prizeText2?.visibility = View.VISIBLE
            prizeText2?.text = "you have earned"

            prizeText.visibility = View.VISIBLE
            prizeText.text = "$pointsWon Points"

            // Start lottie animation if available
            lottieAnimation?.playAnimation()

            crossButton.setOnClickListener {
                try {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                    addPointsToWallet(pointsWon)
                } catch (e: Exception) {
                    addPointsToWallet(pointsWon)
                }
            }

            dialog.setCancelable(false)

            if (!isFinishing && !isDestroyed) {
                dialog.show()
            } else {
                addPointsToWallet(pointsWon)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Won $pointsWon veils!", Toast.LENGTH_SHORT).show()
            addPointsToWallet(pointsWon)
        }
    }

    private fun addPointsToWallet(points: Int) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .update("points", FieldValue.increment(points.toLong()))
                .addOnSuccessListener {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "$points veils added to your account!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Failed to add veils: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    data class Prize(val value: Int, val segment: Int)
}

private fun ObjectAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator) = action()
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    })
}