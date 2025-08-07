package com.growtic.tradeveil

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.growtic.tradeveil.services.AdManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.growtic.tradeveil.R
import java.text.SimpleDateFormat
import java.util.*

class DailyCheckinBottomSheet : BottomSheetDialogFragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var claimButton: Button

    private var currentStreak = 0
    private var lastCheckInDate: Date? = null
    private var isRewardClaimInProgress = false
    private var hasCheckedInToday = false
    private var hasClaimedRewardToday = false

    private val todayIso: String
        get() = isoFormatter.format(Date())

    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    var onRewardClaimed: (() -> Unit)? = null

    // Button states enum for better management
    private enum class ButtonState {
        CHECK_IN,      // Black background, "Check-in"
        CLAIM_REWARD,  // Blue background, "Claim Reward"
        LOADING,       // Blue background, "Loading Ad..."
        CLAIMED        // Gray background, "Claimed"
    }

    /* -------------------------------------------------- */
    /* Fragment life-cycle                                */
    /* -------------------------------------------------- */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.daily_checkin_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        claimButton = view.findViewById<Button>(R.id.btnClaim)

        // Set initial button state
        updateButtonState(ButtonState.CHECK_IN)

        claimButton.setOnClickListener {
            handleButtonClick()
        }

        setupLottieAnimation()
        loadUserCheckInData()
    }

    /* -------------------------------------------------- */
    /* Toast Helper Methods                               */
    /* -------------------------------------------------- */

    private fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        if (isAdded && !isDetached && context != null) {
            Toast.makeText(requireContext(), message, length).show()
        }
    }

    /* -------------------------------------------------- */
    /* Button Click Handler                               */
    /* -------------------------------------------------- */

    private fun handleButtonClick() {
        if (isRewardClaimInProgress) return

        when {
            !hasCheckedInToday -> {
                // Step 1: Check-in
                performCheckIn()
            }
            hasCheckedInToday && !hasClaimedRewardToday -> {
                // Step 2: Claim reward (show ad first)
                claimDailyReward()
            }
            else -> {
                // Already claimed
                showToast("Already claimed today!")
            }
        }
    }

    private fun performCheckIn() {
        isRewardClaimInProgress = true
        val uid = auth.currentUser?.uid ?: return

        val userRef = db.collection("users").document(uid)
        val checkInRef = db.collection("dailyCheckins")
            .document(uid)
            .collection("checkins")
            .document(todayIso)

        db.runTransaction { transaction ->
            // Check if already checked in today
            val checkInSnapshot = transaction.get(checkInRef)
            if (checkInSnapshot.exists()) {
                return@runTransaction "already_checked_in"
            }

            // Get user data
            val userSnapshot = transaction.get(userRef)
            val lastDate = userSnapshot.getTimestamp("lastCheckInDate")?.toDate()
            val oldStreak = userSnapshot.getLong("checkInStreak")?.toInt() ?: 0

            // Calculate new streak
            val newStreak = if (isConsecutive(lastDate)) oldStreak + 1 else 1

            // Create check-in record
            val checkInData = mapOf(
                "checkedIn" to true,
                "timestamp" to com.google.firebase.Timestamp(Date()),
                "streak" to newStreak
            )

            // Update user data
            val updates = mapOf(
                "checkInStreak" to newStreak,
                "lastCheckInDate" to com.google.firebase.Timestamp(Date())
            )

            transaction.set(checkInRef, checkInData)
            transaction.update(userRef, updates)

            "success"
        }.addOnSuccessListener { result ->
            if (!isAdded || isDetached) return@addOnSuccessListener

            isRewardClaimInProgress = false
            when (result) {
                "already_checked_in" -> {
                    hasCheckedInToday = true
                    checkTodayClaimStatus()
                }
                "success" -> {
                    hasCheckedInToday = true
                    hasClaimedRewardToday = false
                    updateButtonState(ButtonState.CLAIM_REWARD)
                    showToast("Checked in successfully!")
                }
            }
        }.addOnFailureListener { e ->
            if (!isAdded || isDetached) return@addOnFailureListener

            isRewardClaimInProgress = false
            showToast("Check-in failed: ${e.localizedMessage}")
        }
    }

    /* -------------------------------------------------- */
    /* Lottie Animation Setup                             */
    /* -------------------------------------------------- */

    private fun setupLottieAnimation() {
        view?.findViewById<LottieAnimationView>(R.id.bottomSheetAnimation)?.apply {
            repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
            playAnimation()
        }
    }

    /* -------------------------------------------------- */
    /* Firestore data                                     */
    /* -------------------------------------------------- */

    private fun loadUserCheckInData() {
        val uid = auth.currentUser?.uid ?: return dismiss()

        // Load streak data first
        loadStreakData(uid)

        // Check today's status
        checkTodayStatus(uid)
    }

    private fun checkTodayStatus(uid: String) {
        // Check if checked in today
        val checkInRef = db.collection("dailyCheckins")
            .document(uid)
            .collection("checkins")
            .document(todayIso)

        checkInRef.get().addOnSuccessListener { checkInSnapshot ->
            if (!isAdded || isDetached) return@addOnSuccessListener

            hasCheckedInToday = checkInSnapshot.exists()

            if (hasCheckedInToday) {
                // If checked in, check claim status
                checkTodayClaimStatus()
            } else {
                // Not checked in yet
                updateButtonState(ButtonState.CHECK_IN)
            }
        }.addOnFailureListener {
            if (!isAdded || isDetached) return@addOnFailureListener

            hasCheckedInToday = false
            updateButtonState(ButtonState.CHECK_IN)
        }
    }

    private fun checkTodayClaimStatus() {
        val uid = auth.currentUser?.uid ?: return

        val claimRef = db.collection("dailyCheckins")
            .document(uid)
            .collection("claims")
            .document(todayIso)

        claimRef.get().addOnSuccessListener { claimSnapshot ->
            if (!isAdded || isDetached) return@addOnSuccessListener

            hasClaimedRewardToday = claimSnapshot.exists()

            when {
                hasClaimedRewardToday -> updateButtonState(ButtonState.CLAIMED)
                hasCheckedInToday -> updateButtonState(ButtonState.CLAIM_REWARD)
                else -> updateButtonState(ButtonState.CHECK_IN)
            }
        }.addOnFailureListener {
            if (!isAdded || isDetached) return@addOnFailureListener

            hasClaimedRewardToday = false
            if (hasCheckedInToday) {
                updateButtonState(ButtonState.CLAIM_REWARD)
            } else {
                updateButtonState(ButtonState.CHECK_IN)
            }
        }
    }

    private fun loadStreakData(uid: String) {
        // Real-time listener for streak updates
        db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded || isDetached) return@addSnapshotListener

                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }

                currentStreak = snapshot.getLong("checkInStreak")?.toInt() ?: 0
                lastCheckInDate = snapshot.getTimestamp("lastCheckInDate")?.toDate()

                // Reset streak if > 48h gap (but don't update Firestore here to avoid loops)
                val effectiveStreak = if (isLastCheckInOver48Hours()) 0 else currentStreak
                updateStreakUI(effectiveStreak)
            }
    }

    /* -------------------------------------------------- */
    /* Reward claim                                       */
    /* -------------------------------------------------- */

    private fun claimDailyReward() {
        if (!hasCheckedInToday) {
            showToast("Please check-in first!")
            return
        }

        if (isRewardClaimInProgress) return

        isRewardClaimInProgress = true
        updateButtonState(ButtonState.LOADING)

        try {
            AdManager.showDailyCheckinAd(requireActivity()) {
                // Ad was shown and dismissed, proceed with reward claim
                proceedWithRewardClaim()
            }
        } catch (e: Exception) {
            // Proceed without ad if there's an error
            proceedWithRewardClaim()
        }
    }

    private fun proceedWithRewardClaim() {
        val uid = auth.currentUser?.uid ?: return

        val claimRef = db.collection("dailyCheckins")
            .document(uid)
            .collection("claims")
            .document(todayIso)

        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            // Check if claim already exists
            val claimSnapshot = transaction.get(claimRef)
            if (claimSnapshot.exists()) {
                return@runTransaction null
            }

            // Get current user data
            val userSnapshot = transaction.get(userRef)
            val currentPoints = userSnapshot.getLong("points") ?: 0
            val totalCheckinPoints = userSnapshot.getLong("totalCheckinPoints") ?: 0
            val streak = userSnapshot.getLong("checkInStreak")?.toInt() ?: 1

            // Calculate reward based on streak
            val reward = calculateReward(streak)

            // Create claim document
            val claimData = mapOf(
                "claimed" to true,
                "timestamp" to com.google.firebase.Timestamp(Date()),
                "reward" to reward,
                "streak" to streak
            )

            // Update user points
            val updates = mapOf(
                "points" to currentPoints + reward,
                "totalCheckinPoints" to totalCheckinPoints + reward
            )

            transaction.set(claimRef, claimData)
            transaction.update(userRef, updates)

            reward
        }.addOnSuccessListener { reward ->
            if (!isAdded || isDetached) return@addOnSuccessListener

            isRewardClaimInProgress = false

            if (reward == null) {
                hasClaimedRewardToday = true
                updateButtonState(ButtonState.CLAIMED)
                showToast("Already claimed today")
            } else {
                hasClaimedRewardToday = true
                updateButtonState(ButtonState.CLAIMED)
                showRewardSuccess(reward as Int)
            }

            onRewardClaimed?.invoke()
        }.addOnFailureListener { e ->
            if (!isAdded || isDetached) return@addOnFailureListener

            isRewardClaimInProgress = false
            updateButtonState(ButtonState.CLAIM_REWARD)
            showRewardError(e)
        }
    }

    private fun calculateReward(streak: Int): Int {
        return when (streak.coerceAtMost(7)) {
            1 -> 10
            2 -> 15
            3 -> 25
            4 -> 40
            5 -> 60
            6 -> 80
            7 -> 100
            else -> 100
        }
    }

    /* -------------------------------------------------- */
    /* UI helpers                                         */
    /* -------------------------------------------------- */

    private fun updateButtonState(state: ButtonState, isLoading: Boolean = false) {
        if (!isAdded || isDetached) return

        claimButton.apply {
            when (state) {
                ButtonState.CHECK_IN -> {
                    text = "Check-in"
                    isEnabled = !isLoading
                    backgroundTintList = ColorStateList.valueOf(Color.BLACK)
                    setTextColor(Color.WHITE)
                }
                ButtonState.CLAIM_REWARD -> {
                    text = "Claim Reward"
                    isEnabled = !isLoading
                    backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.blue_btn)
                    )
                    setTextColor(Color.WHITE)
                }
                ButtonState.LOADING -> {
                    text = "Loading Ad..."
                    isEnabled = false
                    backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.blue_btn)
                    )
                    setTextColor(Color.WHITE)
                }
                ButtonState.CLAIMED -> {
                    text = "Claimed"
                    isEnabled = false
                    backgroundTintList = ColorStateList.valueOf(Color.GRAY)
                    setTextColor(Color.BLACK)
                }
            }
        }
    }

    private fun updateStreakUI(streakDays: Int) {
        if (!isAdded || isDetached) return

        // Use the actual streak value, but check if it should be reset for display
        val displayStreak = if (isLastCheckInOver48Hours()) 0 else streakDays

        view?.findViewById<TextView>(R.id.tvStreak)?.text = displayStreak.toString()

        val rewards = listOf(10, 15, 25, 40, 60, 80, 100)
        val rewardTextIds = listOf(
            R.id.day1_10pt,
            R.id.day2_15pt,
            R.id.day3_25pt,
            R.id.day4_40pt,
            R.id.day5_60pt,
            R.id.day6_80pt,
            R.id.day7_100pt
        )
        val progress = view?.findViewById<LinearLayout>(R.id.rewardProgress) ?: return

        rewards.forEachIndexed { index, pts ->
            val dayView = progress.getChildAt(index) as? LinearLayout ?: return@forEachIndexed
            val claimed = index < displayStreak

            // Update background
            dayView.background = ContextCompat.getDrawable(
                requireContext(),
                if (claimed) R.drawable.daily_checkin_reward_progress_completed_bg
                else R.drawable.daily_checkin_reward_progress_bg
            )

            // Find the specific TextView by ID
            val textView = view?.findViewById<TextView>(rewardTextIds[index])
            textView?.apply {
                text = "${pts}pt"
                setTextColor(
                    if (claimed) Color.WHITE
                    else ContextCompat.getColor(requireContext(), android.R.color.black)
                )
            }
        }
    }

    private fun showRewardSuccess(points: Int) {
        showToast("You earned $points points!")
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded && !isDetached) {
                dismiss()
            }
        }, 2500)
    }

    private fun showRewardError(e: Exception) {
        showToast("Error: ${e.localizedMessage}")
    }

    /* -------------------------------------------------- */
    /* Date utilities                                     */
    /* -------------------------------------------------- */

    private fun isLastCheckInOver48Hours(): Boolean {
        lastCheckInDate ?: return false
        val fortyEightHoursAgo = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -48)
        }.time
        return lastCheckInDate!!.before(fortyEightHoursAgo)
    }

    private fun isConsecutive(lastDate: Date?): Boolean {
        lastDate ?: return false
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DATE, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val lastDateFormatted = isoFormatter.format(lastDate)
        val yesterdayFormatted = isoFormatter.format(yesterday)

        return lastDateFormatted == yesterdayFormatted
    }

    companion object {
        const val TAG = "DailyCheckinBottomSheet"
    }
}