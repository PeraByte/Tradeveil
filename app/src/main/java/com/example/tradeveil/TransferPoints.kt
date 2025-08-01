package com.example.tradeveil

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.tradeveil.services.AdManager
import com.example.tradeveil.services.TransferService
import com.example.tradeveil.services.com.example.tradeveil.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class TransferPoints : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recipientEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var transferButton: Button
    private lateinit var backButton: ImageView
    private lateinit var availablePointsText: TextView
    private lateinit var transferLimitText: TextView
    private lateinit var maxButton: TextView
    private lateinit var pasteButton: LinearLayout
    private lateinit var notificationHelper: NotificationHelper

    // Store current points for max button functionality
    private var currentAvailablePoints = 0L

    // Real-time listener for user points
    private var userListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transfer_points)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        notificationHelper = NotificationHelper(this)

        // Start transfer service for real-time notifications
        startService(Intent(this, TransferService::class.java))

        initViews()
        setupClickListeners()
        listenToUserPoints()
    }

    private fun initViews() {
        try {
            recipientEditText = findViewById(R.id.recipientEditText)
            amountEditText = findViewById(R.id.amountEditText)
            transferButton = findViewById(R.id.transferButton)
            backButton = findViewById(R.id.backButton)
            availablePointsText = findViewById(R.id.availablePointsText)
            transferLimitText = findViewById(R.id.transferLimitText)
            maxButton = findViewById(R.id.maxButton)
            pasteButton = findViewById(R.id.pasteButton)

            transferLimitText.text = "Transfer Limit: 300 - 5,000 points (+ 100 fee)"
            availablePointsText.text = "Available Points: Loading..."
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        transferButton.setOnClickListener {
            try {
                performTransferValidation()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        backButton.setOnClickListener {
            finish()
        }

        // Max button functionality
        maxButton.setOnClickListener {
            try {
                val maxTransferAmount = if (currentAvailablePoints >= 5000) {
                    5000L
                } else {
                    // Consider the fee when calculating max amount
                    val maxWithFee = currentAvailablePoints - 100L // Subtract fee
                    if (maxWithFee >= 300L) maxWithFee else 0L
                }

                if (maxTransferAmount > 0) {
                    amountEditText.setText(maxTransferAmount.toString())
                    amountEditText.setSelection(amountEditText.text.length) // Move cursor to end
                } else {
                    Toast.makeText(this, "Insufficient points for transfer (minimum 400 including fee)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error calculating max amount", Toast.LENGTH_SHORT).show()
            }
        }

        // Paste button functionality
        pasteButton.setOnClickListener {
            try {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip

                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0).text?.toString()?.trim()

                    if (!clipText.isNullOrEmpty()) {
                        // Check if clipboard content is a valid email
                        if (android.util.Patterns.EMAIL_ADDRESS.matcher(clipText).matches()) {
                            recipientEditText.setText(clipText)
                            recipientEditText.setSelection(recipientEditText.text.length)
                        } else {
                            Toast.makeText(this, "Clipboard doesn't contain a valid email", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error accessing clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenToUserPoints() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Error loading points", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                if (snap != null && snap.exists() && !isFinishing && !isDestroyed) {
                    val points = snap.getLong("points") ?: 0
                    val transferPoints = snap.getLong("transferPoints") ?: 0
                    val totalPoints = points + transferPoints
                    currentAvailablePoints = totalPoints
                    availablePointsText.text = "Available Points: ${totalPoints.formatWithCommas()}"
                }
            }
    }

    private fun performTransferValidation() {
        if (isFinishing || isDestroyed) return

        val email = recipientEditText.text.toString().trim()
        val pointsText = amountEditText.text.toString().trim()

        // Clear previous errors
        recipientEditText.error = null
        amountEditText.error = null

        // Enhanced validation
        when {
            email.isEmpty() -> {
                recipientEditText.error = "Email required"
                recipientEditText.requestFocus()
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                recipientEditText.error = "Invalid email format"
                recipientEditText.requestFocus()
                return
            }
            pointsText.isEmpty() -> {
                amountEditText.error = "Points required"
                amountEditText.requestFocus()
                return
            }
        }

        val points = try {
            pointsText.toLong()
        } catch (e: NumberFormatException) {
            amountEditText.error = "Invalid number format"
            amountEditText.requestFocus()
            return
        }

        // Validate point range
        if (points < 300) {
            amountEditText.error = "Minimum transfer is 300 points"
            amountEditText.requestFocus()
            return
        }

        if (points > 5000) {
            amountEditText.error = "Maximum transfer is 5,000 points"
            amountEditText.requestFocus()
            return
        }

        val fee = 100L
        val totalCost = points + fee

        // Parse current points safely
        val currentPointsText = availablePointsText.text.toString()
            .replace("Available Points: ", "")
            .replace(",", "")
            .replace("Loading...", "0")

        val currentPoints = try {
            currentPointsText.toLong()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Error reading current points", Toast.LENGTH_SHORT).show()
            return
        }

        if (totalCost > currentPoints) {
            amountEditText.error = "Insufficient points. Need ${totalCost.formatWithCommas()} (incl. ${fee} fee)"
            amountEditText.requestFocus()
            return
        }

        val currentUserEmail = auth.currentUser?.email
        if (email.equals(currentUserEmail, ignoreCase = true)) {
            recipientEditText.error = "Cannot transfer to yourself"
            recipientEditText.requestFocus()
            return
        }

        // Show ad and proceed with transfer
        try {
            AdManager.showTransferPointsAd(this) {
                if (!isFinishing && !isDestroyed) {
                    lifecycleScope.launch {
                        performTransfer(email, points)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ad loading failed, proceeding with transfer", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                performTransfer(email, points)
            }
        }
    }

    private suspend fun performTransfer(receiverEmail: String, points: Long) {
        if (isFinishing || isDestroyed) return

        val senderId = auth.currentUser?.uid
        val senderMail = auth.currentUser?.email

        if (senderId == null || senderMail == null) {
            Toast.makeText(this, "User authentication error", Toast.LENGTH_SHORT).show()
            return
        }

        val fee = 100L
        val totalCost = points + fee

        // Update UI on main thread
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                transferButton.isEnabled = false
                transferButton.text = "Processing..."
            }
        }

        try {
            // Check if receiver exists
            val receiverSnap = db.collection("users")
                .whereEqualTo("email", receiverEmail)
                .limit(1)
                .get()
                .await()

            if (receiverSnap.isEmpty) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "User not found with email: $receiverEmail", Toast.LENGTH_LONG).show()
                        resetButton()
                    }
                }
                return
            }

            val receiverDoc = receiverSnap.documents[0]
            val receiverId = receiverDoc.id

            // Perform atomic transaction
            db.runTransaction { transaction ->
                val senderRef = db.collection("users").document(senderId)
                val receiverRef = db.collection("users").document(receiverId)
                val transferRef = db.collection("transfers").document()

                // Get current sender balance
                val senderSnap = transaction.get(senderRef)
                val senderPoints = senderSnap.getLong("points") ?: 0
                val senderTransferPoints = senderSnap.getLong("transferPoints") ?: 0
                val totalAvailable = senderPoints + senderTransferPoints

                // Validate sufficient balance
                if (totalAvailable < totalCost) {
                    throw FirebaseFirestoreException(
                        "Insufficient balance. Available: ${totalAvailable.formatWithCommas()}, Required: ${totalCost.formatWithCommas()}",
                        FirebaseFirestoreException.Code.ABORTED
                    )
                }

                // Calculate deduction strategy
                var remainingCost = totalCost
                var pointsToDeduct = 0L
                var transferPointsToDeduct = 0L

                if (senderPoints >= remainingCost) {
                    pointsToDeduct = remainingCost
                } else {
                    pointsToDeduct = senderPoints
                    transferPointsToDeduct = remainingCost - senderPoints
                }

                // Update sender balance
                if (pointsToDeduct > 0) {
                    transaction.update(senderRef, "points", FieldValue.increment(-pointsToDeduct))
                }
                if (transferPointsToDeduct > 0) {
                    transaction.update(senderRef, "transferPoints", FieldValue.increment(-transferPointsToDeduct))
                }

                // Credit receiver (excluding fee)
                transaction.update(receiverRef, "transferPoints", FieldValue.increment(points))

                // Record transaction
                val transferData = mapOf(
                    "senderId" to senderId,
                    "senderEmail" to senderMail,
                    "receiverId" to receiverId,
                    "receiverEmail" to receiverEmail,
                    "points" to points,
                    "fee" to fee,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "completed",
                    "isTransferPoints" to true,
                    "transactionId" to transferRef.id
                )

                transaction.set(transferRef, transferData)
                null
            }.await()

            // Save transfer success notification for sender
            saveTransferNotificationToFirestore(
                "Transfer Successful!",
                "Successfully sent ${points.formatWithCommas()} points to $receiverEmail",
                "transfer_sent"
            )

            // Show success notification to sender
            notificationHelper.showTransferNotification(
                "Transfer Successful!",
                "Sent ${points.formatWithCommas()} points to $receiverEmail",
                isReceived = false
            )

            // Navigate to success screen
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    val intent = Intent(this, TransactionSuccess::class.java).apply {
                        putExtra("points_amount", points.formatWithCommas())
                        putExtra("recipient_email", receiverEmail)
                        putExtra("transaction_fee", fee.toString())
                    }
                    startActivity(intent)
                    finish()
                }
            }

        } catch (e: FirebaseFirestoreException) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, e.message ?: "Transfer failed", Toast.LENGTH_LONG).show()
                    resetButton()
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Transfer error: ${e.message}", Toast.LENGTH_LONG).show()
                    resetButton()
                }
            }
        }
    }

    private suspend fun saveTransferNotificationToFirestore(title: String, message: String, type: String) {
        try {
            val uid = auth.currentUser?.uid ?: return

            val notification = Notification(
                title = title,
                body = message,
                timestamp = Date(),
                type = type
            )

            db.collection("users")
                .document(uid)
                .collection("notifications")
                .add(notification)
                .await()

        } catch (e: Exception) {
            // Handle silently
        }
    }

    private fun resetButton() {
        if (!isFinishing && !isDestroyed) {
            transferButton.isEnabled = true
            transferButton.text = "Transfer Points"
        }
    }

    private fun Long.formatWithCommas(): String = "%,d".format(this)

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
    }
}