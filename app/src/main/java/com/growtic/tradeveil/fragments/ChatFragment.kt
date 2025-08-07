package com.growtic.tradeveil.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.growtic.tradeveil.GlobalChat
import com.growtic.tradeveil.R
import com.growtic.tradeveil.SignUp
import com.growtic.tradeveil.databinding.FragmentChatBinding
import com.growtic.tradeveil.services.AdManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var messageCredits = 0
    private var chatPointsEarned = 0
    private var lastMessageResetTime = 0L
    private var adsWatched = 0
    private var lastAdWatchTime = 0L
    private var isFragmentDestroyed = false

    companion object {
        private const val MAX_ADS_PER_PERIOD = 5
        private const val AD_COOLDOWN_HOURS = 24
        private const val DAILY_MESSAGE_LIMIT = 10
        private const val REWARD_CREDITS = 3
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize AdManager
        AdManager.initialize(requireContext())

        loadUserData()
        setupClickListeners()

        return binding.root
    }

    override fun onDestroyView() {
        isFragmentDestroyed = true
        _binding = null
        super.onDestroyView()
    }

    private fun loadUserData() {
        if (isFragmentDestroyed) return

        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (isFragmentDestroyed || !isAdded) return@addOnSuccessListener

                messageCredits = document.getLong("messageCredits")?.toInt() ?: DAILY_MESSAGE_LIMIT
                chatPointsEarned = document.getLong("chatPointsEarned")?.toInt() ?: 0
                lastMessageResetTime = document.getLong("lastMessageResetTime") ?: System.currentTimeMillis()
                adsWatched = document.getLong("adsWatched")?.toInt() ?: 0
                lastAdWatchTime = document.getLong("lastAdWatchTime") ?: 0L

                checkMessageReset()
                checkAdLimitReset()
                updateUI()
            }
            .addOnFailureListener { e ->
                if (!isFragmentDestroyed && isAdded) {
                }
            }
    }

    private fun checkAdLimitReset() {
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = AD_COOLDOWN_HOURS * 60 * 60 * 1000L

        if (currentTime - lastAdWatchTime > cooldownMillis) {
            adsWatched = 0
            lastAdWatchTime = currentTime
            saveUserData()
        }
    }

    private fun canWatchAd(): Boolean {
        return adsWatched < MAX_ADS_PER_PERIOD
    }

    private fun checkMessageReset() {
        val currentTime = System.currentTimeMillis()
        val twentyFourHours = 24 * 60 * 60 * 1000L

        if (currentTime - lastMessageResetTime > twentyFourHours) {
            messageCredits = DAILY_MESSAGE_LIMIT
            lastMessageResetTime = currentTime
            saveUserData()
        }
    }

    private fun updateUI() {
        if (isFragmentDestroyed || !isAdded || _binding == null) return

        // Update points display
        binding.pointsValue.text = getString(R.string.points_display, chatPointsEarned)

        // Update the pending messages UI based on credits and ad availability
        updatePendingMessagesUI()
    }

    private fun updatePendingMessagesUI() {
        if (isFragmentDestroyed || !isAdded || _binding == null) return

        if (messageCredits <= 0 && canWatchAd()) {
            // User is out of credits but can watch ad
            binding.pendingMessagesButton.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
            )
            binding.pendingMessagesLabel.text = "Watch Ad"
            binding.pendingMessagesLabel.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
            )
            binding.pendingMessagesCount.text = "+$REWARD_CREDITS"
            binding.pendingMessagesCount.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.blue_btn)
            )
            binding.textView9.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
            )
            binding.textView10.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
            )

            // Make it clickable for watching ads
            binding.pendingMessagesCard.isClickable = true
            binding.pendingMessagesCard.isFocusable = true
        } else {
            // User has credits or can't watch ads
            binding.pendingMessagesButton.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.pending_message_button_bg)
            )
            binding.pendingMessagesLabel.text = "Pending Messages"
            binding.pendingMessagesLabel.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
            )
            binding.pendingMessagesCount.text = messageCredits.toString()

            // Change color based on remaining credits
            val colorRes = if (messageCredits <= 0) R.color.red else R.color.green
            binding.pendingMessagesCount.setTextColor(
                ContextCompat.getColor(requireContext(), colorRes)
            )
            binding.textView9.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
            )
            binding.textView10.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.black)
            )

            // Make it show info when clicked
            binding.pendingMessagesCard.isClickable = true
            binding.pendingMessagesCard.isFocusable = true
        }
    }

    private fun setupClickListeners() {
        if (isFragmentDestroyed || _binding == null) return

        binding.privateMessagesButton.setOnClickListener {
            showComingSoonDialog()
        }

        binding.pendingMessagesCard.setOnClickListener {
            handlePendingMessagesClick()
        }

        binding.chatNowBtn.setOnClickListener {
            if (auth.currentUser != null) {
                startActivity(Intent(requireContext(), GlobalChat::class.java))
            } else {
                Toast.makeText(requireContext(), "Please log in to access chat", Toast.LENGTH_SHORT).show()
                startActivity(Intent(requireContext(), SignUp::class.java))
            }
        }
    }

    private fun handlePendingMessagesClick() {
        if (messageCredits <= 0 && canWatchAd()) {
            // Show ad to earn credits
            showRewardAd()
        } else if (messageCredits <= 0 && !canWatchAd()) {
            // User has reached ad limit
            val hoursLeft = 24 - ((System.currentTimeMillis() - lastAdWatchTime) / (60 * 60 * 1000L))
            Toast.makeText(
                requireContext(),
                "Ad limit reached. You can watch $MAX_ADS_PER_PERIOD ads every 24 hours. Try again in ${maxOf(1, hoursLeft)} hours.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // User has credits, show info
            Toast.makeText(
                requireContext(),
                getString(R.string.messages_remaining, messageCredits),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showComingSoonDialog() {
        if (isFragmentDestroyed || !isAdded) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.coming_soon_title)
            .setMessage(R.string.private_messages_coming_soon)
            .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun saveUserData() {
        if (isFragmentDestroyed) return

        val userId = auth.currentUser?.uid ?: return

        val userData = hashMapOf(
            "messageCredits" to messageCredits,
            "chatPointsEarned" to chatPointsEarned,
            "lastMessageResetTime" to lastMessageResetTime,
            "adsWatched" to adsWatched,
            "lastAdWatchTime" to lastAdWatchTime
        )

        db.collection("users").document(userId).set(userData, SetOptions.merge())
            .addOnFailureListener { e ->
                if (!isFragmentDestroyed && isAdded) {
                    Toast.makeText(requireContext(), "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showRewardAd() {
        if (isFragmentDestroyed || !isAdded) return

        if (!canWatchAd()) {
            val hoursLeft = 24 - ((System.currentTimeMillis() - lastAdWatchTime) / (60 * 60 * 1000L))
            Toast.makeText(
                requireContext(),
                "Ad limit reached. Try again in ${maxOf(1, hoursLeft)} hours.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // If ad is null, force re-load and delay before showing
        if (AdManager.chatRewardAd == null) {
            Toast.makeText(requireContext(), "Loading ad, please wait...", Toast.LENGTH_SHORT).show()
            AdManager.initialize(requireContext()) // re-init (won't hurt, guarded by AtomicBoolean)
            AdManager.showChatRewardAd(
                requireActivity(),
                onAdDismissed = {
                    Toast.makeText(requireContext(), "Ad was closed before completion", Toast.LENGTH_SHORT).show()
                },
                onRewardEarned = {
                    adsWatched++
                    if (adsWatched == 1) lastAdWatchTime = System.currentTimeMillis()
                    messageCredits += REWARD_CREDITS
                    saveUserData()
                    updateUI()
                    val remainingAds = MAX_ADS_PER_PERIOD - adsWatched
                    Toast.makeText(
                        requireContext(),
                        "You earned $REWARD_CREDITS message credits! ${if (remainingAds > 0) "You can watch $remainingAds more ad(s) today." else "Ad limit reached for today."}",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onAdFailedToLoad = {
                    Toast.makeText(requireContext(), "Ad is not available right now. Please try again shortly.", Toast.LENGTH_SHORT).show()
                }
            )
            return
        }

        // Otherwise, show the ad immediately
        AdManager.showChatRewardAd(
            requireActivity(),
            onAdDismissed = {
                Toast.makeText(requireContext(), "Ad was closed before completion", Toast.LENGTH_SHORT).show()
            },
            onRewardEarned = {
                adsWatched++
                if (adsWatched == 1) lastAdWatchTime = System.currentTimeMillis()
                messageCredits += REWARD_CREDITS
                saveUserData()
                updateUI()
                val remainingAds = MAX_ADS_PER_PERIOD - adsWatched
                Toast.makeText(
                    requireContext(),
                    "You earned $REWARD_CREDITS message credits! ${if (remainingAds > 0) "You can watch $remainingAds more ad(s) today." else "Ad limit reached for today."}",
                    Toast.LENGTH_LONG
                ).show()
            },
            onAdFailedToLoad = {
                Toast.makeText(requireContext(), "Ad is not available right now. Please try again later.", Toast.LENGTH_SHORT).show()
            }
        )
    }

}