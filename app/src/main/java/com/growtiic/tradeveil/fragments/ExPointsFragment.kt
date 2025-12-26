package com.growtiic.tradeveil.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.growtiic.tradeveil.databinding.FragmentExPointsBinding
import java.text.DecimalFormat

class ExPointsFragment : Fragment() {

    private var _binding: FragmentExPointsBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val decimalFormat = DecimalFormat("#,###.##")

    // Conversion rate: 1 point = $0.005
    private val POINT_TO_DOLLAR_RATE = 0.005

    // Store user's current points
    private var userPoints: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExPointsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        updateConversion()
        fetchUserPoints() // Fetch user points when view is created
    }

    private fun fetchUserPoints() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            return
        }

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userPoints = document.getLong("points") ?: 0
                    // Update max button state based on available points
                    updateMaxButtonState()
                }
            }
            .addOnFailureListener { e ->
                // Handle error silently or show a toast if needed
                userPoints = 0
            }
    }

    private fun updateMaxButtonState() {
        // Enable/disable max button based on whether user has points
        binding.maxButton.isEnabled = userPoints > 0
    }

    private fun setupListeners() {
        binding.maxButton.setOnClickListener {
            // Use the minimum of user's points or 10,000
            val maxAmount = minOf(userPoints, 10000)
            binding.fromAmountInput.setText(maxAmount.toString())
            updateConversion()
        }

        // Add TextWatcher for real-time conversion updates
        binding.fromAmountInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateConversion()
            }
        })

        binding.fromAmountInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateConversion()
            }
        }

        binding.createSwapButton.setOnClickListener {
            checkSwapEligibility()
        }
    }

    private fun updateConversion() {
        try {
            val pointsAmount = binding.fromAmountInput.text.toString().toFloatOrNull() ?: 0.0f
            // Convert points to dollars: 1 point = $0.005
            val dollarAmount = pointsAmount * POINT_TO_DOLLAR_RATE
            binding.toAmountText.text = "${decimalFormat.format(dollarAmount)}"
        } catch (e: Exception) {
            binding.toAmountText.text = "$0.00"
        }
    }

    private fun checkSwapEligibility() {
        val pointsAmount = binding.fromAmountInput.text.toString().toFloatOrNull() ?: 0.0f
        if (pointsAmount <= 0) {
            safeToast("Enter valid amount")
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            safeToast("User not authenticated")
            return
        }

        // Show loading state
        binding.createSwapButton.isEnabled = false
        binding.createSwapButton.text = "Checking..."

        db.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                binding.createSwapButton.isEnabled = true
                binding.createSwapButton.text = "Create Swap"

                if (document.exists()) {
                    val points = document.getLong("points") ?: 0
                    val teamCount = document.getLong("teamCount") ?: 0
                    val transferPoints = document.getLong("transferPoints") ?: 0

                    // Update local points variable
                    userPoints = points

                    when {
                        // First check if user has at least 5000 points
                        points < 5000 -> {
                            showMinimumPointsDialog()
                        }
                        // Then check if user has invited 10 team members
                        teamCount < 10 -> {
                            showTeamMembersRequirementDialog(teamCount)
                        }
                        // Then check if user has enough points for the swap
                        pointsAmount > points -> {
                            showInsufficientPointsDialog(points, transferPoints)
                        }
                        // Show that swap will be available in next update (non-functional)
                        else -> {
                            showNextUpdateDialog()
                        }
                    }
                } else {
                    safeToast("User data not found")
                }
            }
            .addOnFailureListener { e ->
                binding.createSwapButton.isEnabled = true
                binding.createSwapButton.text = "Create Swap"
                safeToast("Error checking eligibility: ${e.message}")
            }
    }

    private fun showMinimumPointsDialog() {
        if (!isAdded || isDetached) return

        AlertDialog.Builder(requireContext())
            .setTitle("Minimum Points Required")
            .setMessage("You need at least 5,000 points to be eligible for swap.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTeamMembersRequirementDialog(currentTeamCount: Long) {
        if (!isAdded || isDetached) return

        AlertDialog.Builder(requireContext())
            .setTitle("Team Members Required")
            .setMessage("You need to invite 10 team members to be eligible for swap.\n\nCurrent team members: $currentTeamCount/10")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInsufficientPointsDialog(regularPoints: Long, transferPoints: Long) {
        if (!isAdded || isDetached) return

        AlertDialog.Builder(requireContext())
            .setTitle("Insufficient Points")
            .setMessage(
                "Available points: ${regularPoints.formatWithCommas()}\n" +
                        "Transfer points: ${transferPoints.formatWithCommas()}\n\n" +
                        "You can only use regular points for swaps. Please enter a lower amount."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showNextUpdateDialog() {
        if (!isAdded || isDetached) return

        AlertDialog.Builder(requireContext())
            .setTitle("Coming Soon")
            .setMessage("Swap feature will be available in the next update of the app.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun clearForm() {
        binding.fromAmountInput.text?.clear()
        binding.toAmountText.text = "$0.00"
    }

    private fun safeToast(message: String) {
        if (isAdded && context != null && !isDetached) {
            try {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
            } catch (e: Exception) {
                // Log but don't crash
            }
        }
    }

    private fun Long.formatWithCommas(): String {
        return "%,d".format(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}