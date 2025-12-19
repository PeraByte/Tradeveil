package com.growwtic.tradeveil

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class Withdraw : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var usdtAddressEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button
    private lateinit var historyButton: Button
    private lateinit var backButton: ImageView

    private val MIN_WITHDRAWAL = 20.0 // $20 minimum
    private val POINT_VALUE = 0.005 // $0.005 per point

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_withdraw)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        usdtAddressEditText = findViewById(R.id.usdtAddressEditText)
        amountEditText = findViewById(R.id.amountEditText)
        confirmButton = findViewById(R.id.confirmButton)
        cancelButton = findViewById(R.id.cancelButton)
        backButton = findViewById(R.id.backButton)

        confirmButton.setOnClickListener {
            validateAndProcessWithdrawal()
        }

        cancelButton.setOnClickListener {
            finish()
        }

        // Set up the back button
        backButton.setOnClickListener {
            finish()
        }

    }

    private fun validateAndProcessWithdrawal() {
        val usdtAddress = usdtAddressEditText.text.toString().trim()
        val amountString = amountEditText.text.toString().trim()

        if (usdtAddress.isEmpty()) {
            usdtAddressEditText.error = "USDT address is required"
            return
        }

        if (amountString.isEmpty()) {
            amountEditText.error = "Amount is required"
            return
        }

        val amount = try {
            amountString.toDouble()
        } catch (e: NumberFormatException) {
            amountEditText.error = "Invalid amount"
            return
        }

        if (amount < MIN_WITHDRAWAL) {
            amountEditText.error = "Minimum withdrawal is $${MIN_WITHDRAWAL}"
            return
        }

        val requiredPoints = (amount / POINT_VALUE).toInt()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@Withdraw, "User not authenticated", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val points = userDoc.getLong("points")?.toInt() ?: 0
                val teamCount = userDoc.getLong("teamCount")?.toInt() ?: 0

                withContext(Dispatchers.Main) {
                    if (points < requiredPoints) {
                        amountEditText.error = "You don't have sufficient points"
                        return@withContext
                    }

                    if (teamCount < 4) { // Changed from 2 to 4 as per requirements
                        showFriendsRequirementDialog()
                        return@withContext
                    }

                    // All requirements met - process withdrawal
                    showWalletRequirementDialog()

                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@Withdraw, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFriendsRequirementDialog() {
        AlertDialog.Builder(this)
            .setTitle("Withdrawal Requirements")
            .setMessage("You must have invited 4 friends to be eligible for withdrawal")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showWalletRequirementDialog() {
        AlertDialog.Builder(this)
            .setTitle("Withdrawal Requirements")
            .setMessage("You must have atleast $40 in your wallet to be eligible for withdrawal")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}