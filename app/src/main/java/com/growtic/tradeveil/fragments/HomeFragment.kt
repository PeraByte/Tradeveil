package com.growtic.tradeveil.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.growtic.tradeveil.DailyCheckinBottomSheet
import com.growtic.tradeveil.ManagePoints
import com.growtic.tradeveil.Missions
import com.growtic.tradeveil.NotificationWorkManager
import com.growtic.tradeveil.Notifications
import com.growtic.tradeveil.R
import com.growtic.tradeveil.Settings
import com.growtic.tradeveil.Spin
import com.growtic.tradeveil.StartQuiz
import com.growtic.tradeveil.Withdrawal
import com.growtic.tradeveil.databinding.FragmentHomeBinding
import com.growtic.tradeveil.services.AdManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.pow

class HomeFragment : Fragment() {

    private companion object {
        const val MAX_DAILY_TICKETS = 10
        const val TICKET_RESET_HOURS = 24
        const val MIN_TICKET_REWARD = 2
        const val MAX_TICKET_REWARD = 16

        const val TWITTER_PROFILE_URL = "https://x.com/TradeVeil/status/1952993446119260507"
        const val YOUTUBE_CHANNEL_URL = "https://www.youtube.com/@TradeVeil"
        const val TELEGRAM_GROUP_URL = "https://t.me/tradeveil/4"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var availableTickets = 0
    private var lastTicketTimestamp: Long = 0
    private var isLoadingTicketAd = false
    private var isLoadingOpenAd = false

    // Notification permission handling
    private lateinit var notificationWorkManager: NotificationWorkManager
    private var hasShownPermissionDialog = false

    // Permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        handleNotificationPermissionResult(isGranted)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize notification work manager
        notificationWorkManager = NotificationWorkManager(requireContext())

        setupClickListeners()
        loadUserData()
        setupInitialAnimations()

        // Check notification permissions after a short delay to let UI settle
        Handler(Looper.getMainLooper()).postDelayed({
            checkAndRequestNotificationPermission()
        }, 1000)
    }

    private fun checkAndRequestNotificationPermission() {
        if (!isAdded || hasShownPermissionDialog) return

        val hasPermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ requires POST_NOTIFICATIONS permission
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                // For older versions, check if notifications are enabled
                NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            }
        }

        if (!hasPermission) {
            requestNotificationPermission()
        } else {
            // Permission granted, setup notifications
            setupNotifications()
        }
    }

    private fun requestNotificationPermission() {
        if (hasShownPermissionDialog) return
        hasShownPermissionDialog = true

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ - request POST_NOTIFICATIONS permission (shows system dialog)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                // For older versions, notifications are enabled by default
                // Just setup notifications
                setupNotifications()
            }
        }
    }

    private fun handleNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            Toast.makeText(
                requireContext(),
                "Notifications enabled! You'll get daily reminders",
                Toast.LENGTH_LONG
            ).show()
            setupNotifications()
        } else {
            // Permission denied - just continue without notifications
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Please go to Settings > Apps > TradeVeil > Notifications",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupNotifications() {
        auth.currentUser?.let { user ->
            try {
                // Get username for personalized notifications
                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { document ->
                        val username = document.getString("username")

                        // Schedule notifications if not already scheduled
                        notificationWorkManager.scheduleRemindersIfNotScheduled(username) }
                    .addOnFailureListener { e ->
                        // Setup without username
                        notificationWorkManager.scheduleRemindersIfNotScheduled()
                    }
            } catch (e: Exception) {
            }
        }
    }


    private fun calculateLevelFromPoints(points: Long): Long {
        return (points / 1000) + 1
    }

    // CHANGE 2: Updated for new level system - each level requires 1000 points
    private fun getPointsRequiredForLevel(level: Long): Long {
        return (level - 1) * 1000
    }

    // CHANGE 3: New function to calculate rank from points
    private fun calculateRankFromPoints(points: Long): String {
        return when {
            points < 500 -> "Spark"
            points < 1000 -> "Surge"
            points < 2000 -> "Vanguard"
            points < 3500 -> "OG Veiler"
            else -> "Crypto Guru"
        }
    }

    // CHANGE 3: New function to determine higher rank (prevents decrease)
    private fun getHigherRank(newRank: String, currentRank: String): String {
        val rankHierarchy = listOf("Spark", "Surge", "Vanguard", "OG Veiler", "Crypto Guru")
        val newRankIndex = rankHierarchy.indexOf(newRank)
        val currentRankIndex = rankHierarchy.indexOf(currentRank)

        return if (newRankIndex > currentRankIndex) newRank else currentRank
    }

    // CHANGE 3: Updated to accept rank as parameter instead of calculating from points
    private fun updateUserRankAndMedal(rank: String) {
        safeBinding { binding ->
            val medalDrawable = when (rank) {
                "Spark" -> R.drawable.spark
                "Surge" -> R.drawable.surge
                "Vanguard" -> R.drawable.vanguard
                "OG Veiler" -> R.drawable.og_veiler
                "Crypto Guru" -> R.drawable.crypto_guru
                else -> R.drawable.spark
            }

            binding.userRank.text = rank
            binding.medalIcon.setImageResource(medalDrawable)
        }
    }

    // CHANGE 3: Updated to handle both level and rank updates with highest tracking
    private fun updateUserLevelAndRank(userId: String, newLevel: Long, newRank: String, oldHighestLevel: Long, oldHighestRank: String) {
        val updates = mutableMapOf<String, Any>()
        var showLevelUpMessage = false
        var showRankUpMessage = false

        if (newLevel > oldHighestLevel) {
            updates["level"] = newLevel
            updates["highestLevel"] = newLevel
            showLevelUpMessage = true
        }

        if (newRank != oldHighestRank) {
            updates["highestRank"] = newRank
            showRankUpMessage = true
        }

        if (updates.isNotEmpty()) {
            db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener {
                    if (isAdded) {
                        when {
                            showLevelUpMessage && showRankUpMessage -> {
                                Toast.makeText(context, "Level Up! You're now level $newLevel and achieved $newRank rank!", Toast.LENGTH_LONG).show()
                            }
                            showLevelUpMessage -> {
                                Toast.makeText(context, "Level Up! You're now level $newLevel", Toast.LENGTH_LONG).show()
                            }
                            showRankUpMessage -> {
                                Toast.makeText(context, "Rank Up! You've achieved $newRank rank!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                }
        }
    }

    // CHANGE 2 & 3: Updated loadUserData function to handle new level system and prevent rank/level decrease
    private fun loadUserData() {
        auth.currentUser?.uid?.let { userId ->
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (!isAdded || _binding == null) {
                        return@addOnSuccessListener
                    }

                    if (document.exists()) {
                        val username = document.getString("username") ?: "User"
                        val currentPoints = document.getLong("points") ?: 0L
                        val currentStreak = document.getLong("checkInStreak") ?: 0L
                        val teamCount = document.getLong("teamCount") ?: 0L
                        val storedLevel = document.getLong("level") ?: 1L
                        val totalCheckinPoints = document.getLong("totalCheckinPoints") ?: 0L

                        // CHANGE 3: Get stored highest level and rank to prevent decrease
                        val storedHighestLevel = document.getLong("highestLevel") ?: 1L
                        val storedHighestRank = document.getString("highestRank") ?: "Spark"

                        // CHANGE 2 & 3: Calculate level based on new system but never decrease
                        val calculatedLevel = calculateLevelFromPoints(currentPoints)
                        val finalLevel = max(calculatedLevel, storedHighestLevel)

                        // CHANGE 3: Calculate rank but never decrease
                        val calculatedRank = calculateRankFromPoints(currentPoints)
                        val finalRank = getHigherRank(calculatedRank, storedHighestRank)

                        // Update level and rank if they've increased
                        if (finalLevel > storedLevel || finalRank != storedHighestRank) {
                            updateUserLevelAndRank(userId, finalLevel, finalRank, storedHighestLevel, storedHighestRank)
                        }

                        val currentLevel = finalLevel
                        val (progressPercent, pointsToNextLevel) =
                            calculateProgressToNextLevel(currentPoints, currentLevel)

                        loadProfilePicture()
                        fetchGlobalRank()

                        updateUI(
                            username = username,
                            level = currentLevel,
                            points = currentPoints,
                            streak = currentStreak,
                            teamCount = teamCount,
                            progressPercent = progressPercent,
                            pointsToNextLevel = pointsToNextLevel,
                            totalCheckinPoints = totalCheckinPoints,
                            userRank = finalRank
                        )
                    }
                }
                .addOnFailureListener { e ->
                    if (isAdded) {
                        Toast.makeText(context, "Failed to load user data", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    // CHANGE 3: Updated updateUI function to accept userRank parameter
    private fun updateUI(
        username: String,
        level: Long,
        points: Long,
        streak: Long,
        teamCount: Long,
        progressPercent: Int = 0,
        pointsToNextLevel: Long = 0,
        totalCheckinPoints: Long = 0,
        userRank: String = "Spark"
    ) {
        safeBinding { binding ->
            binding.apply {
                usernameText.text = username
                currentLevelValue.text = level.toString()
                earnedPointsValue.text = NumberFormat.getInstance().format(points)
                teamMembersNumber.text = teamCount.toString()
                rewardPointsValue.text = NumberFormat.getInstance().format(totalCheckinPoints)

                // CHANGE 3: Update user rank and medal with the final rank (never decrease)
                updateUserRankAndMedal(userRank)

                updateStepIndicators(streak.toInt())
            }
        }
    }

    private fun setupInitialAnimations() {
        // Add subtle entrance animations for key elements
        val slideInFromTop = AnimationUtils.loadAnimation(requireContext(), android.R.anim.slide_in_left)
        val fadeIn = AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in)

        binding.apply {
            profileAvatar.startAnimation(fadeIn)
            usernameText.startAnimation(slideInFromTop)
            earnedPointsCard.startAnimation(fadeIn)
        }
    }

    private fun safeBinding(action: (FragmentHomeBinding) -> Unit) {
        if (_binding != null && isAdded) {
            action(binding)
        }
    }

    private fun setupClickListeners() {
        binding.checkInButton.setOnClickListener {
            animateButtonClick(it) { checkUserAndShowBottomSheet() }
        }
        binding.notificationButton.setOnClickListener {
            animateButtonClick(it) { navigateWithSlideAnimation(Notifications::class.java) }
        }
        binding.settingsButton.setOnClickListener {
            animateButtonClick(it) { navigateWithSlideAnimation(Settings::class.java) }
        }
        binding.earnedPointsCard.setOnClickListener {
            animateCardClick(it) { navigateWithSlideAnimation(Withdrawal::class.java) }
        }
        binding.quizButton.setOnClickListener {
            animateButtonClick(it) { navigateWithSlideAnimation(StartQuiz::class.java) }
        }
        binding.missionsButton.setOnClickListener {
            animateButtonClick(it) { navigateWithSlideAnimation(Missions::class.java) }
        }
        binding.walletButton.setOnClickListener {
            animateButtonClick(it) { navigateWithSlideAnimation(ManagePoints::class.java) }
        }
        binding.getStartButton.setOnClickListener {
            animateButtonClick(it) { navigateWithSlideAnimation(Spin::class.java) }
        }
        binding.giftIcon.setOnClickListener {
            animateButtonClick(it) { navigateWithSlideAnimation(Spin::class.java) }
        }

        binding.shopButton.setOnClickListener {
            animateButtonClick(it) {
                Toast.makeText(context, "Shop isn't available yet! Coming soon...", Toast.LENGTH_LONG).show()
            }
        }

        // Social media buttons with enhanced animations
        binding.twitterCard.setOnClickListener {
            animateSocialMediaClick(it) { openTwitterProfile() }
        }
        binding.youtubeCard.setOnClickListener {
            animateSocialMediaClick(it) { openYouTubeChannel() }
        }
        binding.telegramCard.setOnClickListener {
            animateSocialMediaClick(it) { openTelegramGroup() }
        }

        setupTicketSystem()
    }

    private fun animateButtonClick(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    private fun animateCardClick(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    private fun animateSocialMediaClick(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    // Update the social media click handlers to remove the extra animation call
    private fun openTwitterProfile() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TWITTER_PROFILE_URL)))
        } catch (e: Exception) {
            showSocialMediaError("Twitter")
        }
    }

    private fun openYouTubeChannel() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_CHANNEL_URL)))
        } catch (e: Exception) {
            showSocialMediaError("YouTube")
        }
    }

    private fun openTelegramGroup() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_GROUP_URL)))
        } catch (e: Exception) {
            showSocialMediaError("Telegram")
        }
    }


    private fun navigateWithSlideAnimation(activityClass: Class<*>) {
        val intent = Intent(requireContext(), activityClass)
        startActivity(intent)

        // Apply slide animation based on the activity type
        when (activityClass) {
            Settings::class.java, Notifications::class.java -> {
                // Slide from right for settings/notifications
                requireActivity().overridePendingTransition(
                    R.anim.slide_in_right, R.anim.slide_out_left
                )
            }
            Withdrawal::class.java, ManagePoints::class.java -> {
                // Slide up for wallet-related activities
                requireActivity().overridePendingTransition(
                    R.anim.slide_in_right, R.anim.slide_out_left
                )
            }
            StartQuiz::class.java, Missions::class.java -> {
                // Slide from left for game-related activities
                requireActivity().overridePendingTransition(
                    R.anim.slide_in_right, R.anim.slide_out_left
                )
            }
            else -> {
                // Default fade animation
                requireActivity().overridePendingTransition(
                    android.R.anim.fade_in, android.R.anim.fade_out
                )
            }
        }
    }

    private fun checkUserAndShowBottomSheet() {
        auth.currentUser?.let {
            DailyCheckinBottomSheet().apply {
                onRewardClaimed = {
                    // Add a small delay to ensure smooth animation
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadUserData()
                    }, 300)
                }
            }.show(parentFragmentManager, DailyCheckinBottomSheet.TAG)
        } ?: run {
            Toast.makeText(context, "🔐 Please login first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(
        username: String,
        level: Long,
        points: Long,
        streak: Long,
        teamCount: Long,
        progressPercent: Int = 0,
        pointsToNextLevel: Long = 0,
        totalCheckinPoints: Long = 0
    ) {
        safeBinding { binding ->
            binding.apply {
                usernameText.text = username
                currentLevelValue.text = level.toString()
                earnedPointsValue.text = NumberFormat.getInstance().format(points)
                teamMembersNumber.text = teamCount.toString()
                rewardPointsValue.text = NumberFormat.getInstance().format(totalCheckinPoints)

                // Update user rank and medal based on points
                updateUserRankAndMedal(points)

                updateStepIndicators(streak.toInt())
            }
        }
    }

    private fun updateUserRankAndMedal(points: Long) {
        safeBinding { binding ->
            val (rankText, medalDrawable) = when {
                points < 500 -> Pair("Spark", R.drawable.spark)
                points < 1000 -> Pair("Surge", R.drawable.surge)
                points < 2000 -> Pair("Vanguard", R.drawable.vanguard)
                points < 3500 -> Pair("OG Veiler", R.drawable.og_veiler)
                else -> Pair("Crypto Guru", R.drawable.crypto_guru)
            }

            binding.userRank.text = rankText
            binding.medalIcon.setImageResource(medalDrawable)
        }
    }

    private fun updateStepIndicators(currentStreak: Int) {
        val maxStreak = 7
        val progressIndicator = binding.rewardsProgressIndicator
        val activeSteps = currentStreak.coerceAtMost(maxStreak)

        for (i in 0 until progressIndicator.childCount) {
            val stepView = progressIndicator.getChildAt(i)
            val isActive = i < activeSteps

            val states = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_activated),
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.home_dashed_step_active
                    )
                )
                addState(
                    intArrayOf(),
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.home_dashed_step_inactive
                    )
                )
            }

            stepView.background = states
            stepView.isActivated = isActive

            if (i == activeSteps - 1) {
                stepView.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(300)
                    .withEndAction {
                        stepView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
        }
    }

    private fun calculateProgressToNextLevel(
        points: Long,
        currentLevel: Long
    ): Pair<Int, Long> {
        val currentLevelPoints = getPointsRequiredForLevel(currentLevel)
        val nextLevelPoints = getPointsRequiredForLevel(currentLevel + 1)

        val pointsInCurrentLevel = points - currentLevelPoints
        val pointsNeededForNextLevel = nextLevelPoints - currentLevelPoints

        val progress = if (pointsNeededForNextLevel > 0) {
            ((pointsInCurrentLevel.toDouble() / pointsNeededForNextLevel) * 100).toInt()
        } else {
            100
        }

        val pointsToNext = nextLevelPoints - points

        return Pair(progress.coerceIn(0, 100), pointsToNext.coerceAtLeast(0))
    }

    private fun setupTicketSystem() {
        auth.currentUser?.uid?.let { userId ->
            db.collection("userTickets").document(userId).get()
                .addOnSuccessListener { document ->
                    if (!isAdded || _binding == null) {
                        return@addOnSuccessListener
                    }

                    if (document.exists()) {
                        availableTickets = document.getLong("availableTickets")?.toInt() ?: 0
                        lastTicketTimestamp = document.getLong("lastTicketTimestamp") ?: System.currentTimeMillis()

                        if (shouldResetTickets()) {
                            resetTickets()
                        } else {
                            updateTicketUI()
                        }
                    } else {
                        initializeTicketData(userId)
                    }
                }
                .addOnFailureListener { e ->
                    if (isAdded) {
                        Toast.makeText(context, "Failed to load ticket data", Toast.LENGTH_SHORT).show()
                    }
                }

            safeBinding { binding ->
                binding.getTicketButton.setOnClickListener {
                    animateTicketButton(it) { handleGetTicketClick() }
                }

                binding.openTicketButton.setOnClickListener {
                    animateTicketButton(it) { handleOpenTicketClick() }
                }
            }
        } ?: run {
            safeBinding { binding ->
                binding.getTicketButton.setOnClickListener {
                    Toast.makeText(requireContext(), "🔐 Please login first", Toast.LENGTH_SHORT).show()
                }
                binding.openTicketButton.setOnClickListener {
                    Toast.makeText(requireContext(), "🔐 Please login first", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun animateTicketButton(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    private fun handleGetTicketClick() {
        when {
            auth.currentUser == null -> {
                Toast.makeText(requireContext(), "🔐 Please login first", Toast.LENGTH_SHORT).show()
            }
            isLoadingTicketAd -> {
                Toast.makeText(requireContext(), "Ad is loading, please wait...", Toast.LENGTH_SHORT).show()
            }
            availableTickets >= MAX_DAILY_TICKETS -> {
                Toast.makeText(
                    requireContext(),
                    "Daily limit reached! You have maximum tickets ($availableTickets/$MAX_DAILY_TICKETS)\nCome back tomorrow for more!",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                showLoadingToast("Loading ad to earn ticket...")
                showRewardedAdForTicket()
            }
        }
    }

    private fun handleOpenTicketClick() {
        when {
            auth.currentUser == null -> {
                Toast.makeText(requireContext(), "🔐 Please login first", Toast.LENGTH_SHORT).show()
            }
            isLoadingOpenAd -> {
                Toast.makeText(requireContext(), "Ad is loading, please wait...", Toast.LENGTH_SHORT).show()
            }
            availableTickets <= 0 -> {
                Toast.makeText(
                    requireContext(),
                    "No tickets available!",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                showLoadingToast("Loading ad to open ticket...")
                showRewardedAdForOpeningTicket()
            }
        }
    }

    private fun showLoadingToast(message: String) {
        Toast.makeText(requireContext(), "$message", Toast.LENGTH_SHORT).show()
    }

    private fun showRewardedAdForTicket() {
        if (isLoadingTicketAd) return

        isLoadingTicketAd = true
        updateButtonStates()

        try {
            AdManager.showGetTicketAd(requireActivity(),
                onAdDismissed = {
                    isLoadingTicketAd = false
                    updateButtonStates()
                    if (isAdded && _binding != null) {
                        Toast.makeText(requireContext(), "Ad was closed before completion. No ticket earned.", Toast.LENGTH_SHORT).show()
                    }
                },
                onRewardEarned = {
                    isLoadingTicketAd = false
                    updateButtonStates()
                    if (isAdded && _binding != null) {
                        addTicket()
                    }
                },
                onAdFailedToLoad = {
                    isLoadingTicketAd = false
                    updateButtonStates()
                    if (isAdded && _binding != null) {
                        Toast.makeText(
                            requireContext(),
                            "Ad not available right now. Please try again in a few moments.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        } catch (e: Exception) {
            isLoadingTicketAd = false
            updateButtonStates()
            if (isAdded && _binding != null) {
                Toast.makeText(
                    requireContext(),
                    "Ad service unavailable. Please try again later.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRewardedAdForOpeningTicket() {
        if (isLoadingOpenAd) return

        isLoadingOpenAd = true
        updateButtonStates()

        try {
            AdManager.showOpenTicketAd(requireActivity(),
                onAdDismissed = {
                    isLoadingOpenAd = false
                    updateButtonStates()
                    if (isAdded && _binding != null) {
                        Toast.makeText(requireContext(), "Ad was closed before completion. Ticket not opened.", Toast.LENGTH_SHORT).show()
                    }
                },
                onRewardEarned = {
                    isLoadingOpenAd = false
                    updateButtonStates()
                    if (isAdded && _binding != null) {
                        openTicket()
                    }
                },
                onAdFailedToLoad = {
                    isLoadingOpenAd = false
                    updateButtonStates()
                    if (isAdded && _binding != null) {
                        Toast.makeText(
                            requireContext(),
                            "Ad not available right now. Please try again in a few moments.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        } catch (e: Exception) {
            isLoadingOpenAd = false
            updateButtonStates()
            if (isAdded && _binding != null) {
                Toast.makeText(
                    requireContext(),
                    "Ad service unavailable. Please try again later.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateButtonStates() {
        safeBinding { binding ->
            // Visual feedback for loading states
            binding.getTicketButton.alpha = if (isLoadingTicketAd) 0.6f else 1.0f
            binding.openTicketButton.alpha = if (isLoadingOpenAd) 0.6f else 1.0f
        }
    }

    private fun addTicket() {
        if (availableTickets >= MAX_DAILY_TICKETS) {
            Toast.makeText(
                requireContext(),
                "Daily limit already reached! Maximum $MAX_DAILY_TICKETS tickets per day",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val oldTicketCount = availableTickets
        availableTickets++
        lastTicketTimestamp = System.currentTimeMillis()

        updateTicketData { success ->
            if (success) {
                updateTicketUI()
                showTicketEarnedAnimation()
                Toast.makeText(
                    requireContext(),
                    "Ticket earned! You now have $availableTickets/$MAX_DAILY_TICKETS tickets",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Rollback on failure
                availableTickets = oldTicketCount
                Toast.makeText(
                    requireContext(),
                    "Failed to save ticket. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showTicketEarnedAnimation() {
        safeBinding { binding ->
            binding.availableTicketsCount.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(200)
                .withEndAction {
                    binding.availableTicketsCount.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }
    }

    private fun openTicket() {
        if (availableTickets <= 0) {
            Toast.makeText(
                requireContext(),
                "No tickets available! Get more by watching ads",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val randomPoints = (MIN_TICKET_REWARD..MAX_TICKET_REWARD).random()
        val oldTicketCount = availableTickets
        availableTickets--


        updateTicketData { ticketUpdateSuccess ->
            if (ticketUpdateSuccess) {
                addPointsToWallet(randomPoints) { pointsUpdateSuccess ->
                    if (pointsUpdateSuccess) {
                        showRewardDialog(randomPoints)
                        updateTicketUI()

                        // Refresh user data with a small delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadUserData()
                        }, 500)

                    } else {
                        // Rollback ticket count
                        availableTickets = oldTicketCount
                        updateTicketData { }
                        updateTicketUI()
                        Toast.makeText(
                            requireContext(),
                            "Failed to add points. Ticket restored. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                // Rollback ticket count
                availableTickets = oldTicketCount
                Toast.makeText(
                    requireContext(),
                    "Failed to update ticket count. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showRewardDialog(pointsEarned: Int) {
        if (!isAdded || _binding == null) return

        try {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_prize, null)
            val prizeText = dialogView.findViewById<TextView>(R.id.prizeText)
            prizeText.text = "$pointsEarned Points"

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val crossButton = dialogView.findViewById<ImageButton>(R.id.crossButton)
            crossButton.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()

            // Add celebration animation to the dialog
            prizeText.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(300)
                .withEndAction {
                    prizeText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start()
                }
                .start()


        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Congratulations! You earned $pointsEarned points!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun addPointsToWallet(points: Int, callback: (Boolean) -> Unit) {
        auth.currentUser?.uid?.let { userId ->
            db.collection("users").document(userId)
                .update("points", FieldValue.increment(points.toLong()))
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener { e ->
                    callback(false)
                }
        } ?: run {
            callback(false)
        }
    }

    private fun updateTicketData(callback: (Boolean) -> Unit = {}) {
        auth.currentUser?.uid?.let { userId ->
            val ticketData = hashMapOf(
                "availableTickets" to availableTickets,
                "lastTicketTimestamp" to lastTicketTimestamp
            )

            db.collection("userTickets").document(userId)
                .set(ticketData)
                .addOnSuccessListener {
                    callback(true)
                }
                .addOnFailureListener { e ->
                    callback(false)
                }
        } ?: run {
            callback(false)
        }
    }

    private fun initializeTicketData(userId: String) {
        val ticketData = hashMapOf(
            "availableTickets" to 0,
            "lastTicketTimestamp" to System.currentTimeMillis()
        )

        db.collection("userTickets").document(userId)
            .set(ticketData)
            .addOnSuccessListener {
                availableTickets = 0
                lastTicketTimestamp = System.currentTimeMillis()
                updateTicketUI()
            }
            .addOnFailureListener { e ->
            }
    }

    private fun shouldResetTickets(): Boolean {
        if (lastTicketTimestamp == 0L) return false

        val currentTime = System.currentTimeMillis()
        val hoursPassed = (currentTime - lastTicketTimestamp) / (1000 * 60 * 60)

        return hoursPassed >= TICKET_RESET_HOURS
    }

    private fun resetTickets() {
        val oldTickets = availableTickets
        availableTickets = 0
        lastTicketTimestamp = System.currentTimeMillis()

        updateTicketData { success ->
            if (success) {
                updateTicketUI()
                if (oldTickets > 0) {
                    Toast.makeText(
                        requireContext(),
                        "Daily ticket limit reset! You can earn up to $MAX_DAILY_TICKETS tickets today",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // Retry once on failure
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isAdded) resetTickets()
                }, 1000)
            }
        }
    }

    private fun updateTicketUI() {
        safeBinding { binding ->
            binding.availableTicketsCount.text = availableTickets.toString()

            // Pulse animation when tickets change
            binding.availableTicketsCount.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200)
                .withEndAction {
                    binding.availableTicketsCount.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()

        }
    }

    /* ------------------------------------------------------------------
       Everything that was after resetTickets in the ORIGINAL file
       ------------------------------------------------------------------ */

    private fun showSocialMediaError(platform: String) {
        Toast.makeText(
            requireContext(),
            "Couldn't open $platform. Please check if the app is installed.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Replace the loadProfilePicture() method in your HomeFragment with this simple version:

    private fun loadProfilePicture() {
        auth.currentUser?.uid?.let { userId ->
            val storageRef = Firebase.storage.reference
            // Same path as EditProfile: profile_images/{userId}/profile.jpg
            val profileImageRef = storageRef.child("profile_images/$userId/profile.jpg")

            profileImageRef.downloadUrl
                .addOnSuccessListener { uri ->
                    safeBinding {
                        Glide.with(requireContext())
                            .load(uri)
                            .placeholder(R.drawable.avatar)
                            .error(R.drawable.avatar)
                            .circleCrop()
                            .into(binding.profileAvatar)
                    }
                }
                .addOnFailureListener {
                    safeBinding {
                        binding.profileAvatar.setImageResource(R.drawable.avatar)
                    }
                }
        } ?: safeBinding {
            binding.profileAvatar.setImageResource(R.drawable.avatar)
        }
    }



    private fun fetchGlobalRank() {
        auth.currentUser?.uid?.let { userId ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .orderBy("points", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { snapshot ->
                    val rank = snapshot.documents.indexOfFirst { it.id == userId } + 1
                    safeBinding {
                        binding.globalRankValue.text = NumberFormat.getInstance().format(rank.toLong())
                    }
                }
                .addOnFailureListener { e ->
                    safeBinding {
                        binding.globalRankValue.text = "N/A"
                    }
                }
        } ?: safeBinding {
            binding.globalRankValue.text = "Login"
        }
    }

    /* ------------------------------------------------------------------
       Lifecycle overrides (upgraded with AdManager hooks)
       ------------------------------------------------------------------ */

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        AdManager.pauseAds()
    }


    override fun onResume() {
        super.onResume()
        AdManager.resumeAds()
        updateTicketUI()
        // Refresh profile picture when returning to home
        loadProfilePicture()
    }
}