package com.growtic.tradeveil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.growtic.tradeveil.utils.ReferralUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class SignUp : AppCompatActivity() {
    companion object {
        const val REFERRAL_REWARD_POINTS = 20
        const val REFERRAL_BONUS_POINTS = 10
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var referralEditText: EditText
    private lateinit var termsCheckBox: CheckBox
    private lateinit var continueButton: Button
    private lateinit var loginRedirectText: TextView
    private lateinit var termsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_sign_up)

        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            usernameEditText = findViewById(R.id.signup_username_input)
            emailEditText = findViewById(R.id.signup_email_input)
            passwordEditText = findViewById(R.id.signup_password_input)
            referralEditText = findViewById(R.id.signup_referral_input)
            termsCheckBox = findViewById(R.id.signup_terms_checkbox)
            continueButton = findViewById(R.id.signup_continue_button)
            loginRedirectText = findViewById(R.id.signup_login_button)
            termsButton = findViewById(R.id.signup_terms_button)

            continueButton.setOnClickListener {
                val username = usernameEditText.text.toString().trim()
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val referralCode = referralEditText.text.toString().trim()
                val termsChecked = termsCheckBox.isChecked

                if (validateInputs(username, email, password, termsChecked)) {
                    signUpUser(username, email, password, referralCode)
                }
            }

            loginRedirectText.setOnClickListener {
                startActivity(Intent(this, Login::class.java))
                finish()
            }

            termsButton.setOnClickListener {
                openWebsite("https://www.tradeveil.com/terms-of-service")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Initialization error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebsite(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open website", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(username: String, email: String, password: String, termsChecked: Boolean): Boolean {
        return when {
            username.isEmpty() -> {
                usernameEditText.error = "Username is required"
                false
            }
            email.isEmpty() -> {
                emailEditText.error = "Email is required"
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailEditText.error = "Enter a valid email"
                false
            }
            password.isEmpty() -> {
                passwordEditText.error = "Password is required"
                false
            }
            password.length < 6 -> {
                passwordEditText.error = "Password must be at least 6 characters"
                false
            }
            !termsChecked -> {
                Toast.makeText(this, "You must agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun signUpUser(username: String, email: String, password: String, referralCode: String) {
        continueButton.isEnabled = false
        continueButton.text = "Creating account..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user == null) {
                        handleSignUpFailure("User creation failed - null user")
                        return@addOnCompleteListener
                    }

                    val profileUpdates = userProfileChangeRequest {
                        displayName = username
                    }

                    user.updateProfile(profileUpdates).addOnCompleteListener { profileTask ->
                        if (profileTask.isSuccessful) {
                            lifecycleScope.launch {
                                try {
                                    completeRegistration(user, username, referralCode)
                                    createUserWallet(user)
                                    sendFirebaseVerificationEmail(user)
                                } catch (e: Exception) {
                                    handleSignUpFailure("Registration failed: ${e.message}")
                                    user.delete().addOnCompleteListener {
                                        // User account deleted due to failure
                                    }
                                }
                            }
                        } else {
                            handleSignUpFailure("Failed to update profile: ${profileTask.exception?.message}")
                        }
                    }
                } else {
                    handleSignUpFailure("Sign up failed: ${task.exception?.message}")
                }
            }
    }

    private fun handleSignUpFailure(message: String) {
        runOnUiThread {
            continueButton.isEnabled = true
            continueButton.text = "Continue"
            Toast.makeText(this@SignUp, message, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun completeRegistration(user: FirebaseUser, username: String, referralCode: String) {
        try {
            val userReferralCode = ReferralUtils.generateUniqueCode(user.uid)

            val userData = hashMapOf(
                "username" to username,
                "email" to user.email,
                "referralCode" to userReferralCode,
                "points" to if (referralCode.isNotEmpty()) REFERRAL_BONUS_POINTS else 0,
                "teamCount" to 0,
                "totalReferralEarnings" to 0,
                "createdAt" to Calendar.getInstance().timeInMillis,
                "referredBy" to if (referralCode.isNotEmpty()) referralCode else null,
                "checkInStreak" to 0,
                "lastCheckInDate" to null,
                "verified" to false,
                "joinedViaReferral" to referralCode.isNotEmpty(),
                "referralBonusReceived" to if (referralCode.isNotEmpty()) REFERRAL_BONUS_POINTS else 0
            )

            val quizStats = hashMapOf(
                "completed" to 0,
                "allCompleted" to false,
                "lastAttempt" to null,
                "attempts" to 0
            )

            val batch = db.batch()

            val userRef = db.collection("users").document(user.uid)
            batch.set(userRef, userData)

            val quizRef = userRef.collection("quizProgress").document("stats")
            batch.set(quizRef, quizStats)

            val referralRef = db.collection("referralCodes").document(userReferralCode)
            batch.set(referralRef, hashMapOf("userId" to user.uid))

            if (referralCode.isNotEmpty()) {
                val referrerQuery = db.collection("users")
                    .whereEqualTo("referralCode", referralCode)
                    .limit(1)
                    .get()
                    .await()

                if (!referrerQuery.isEmpty) {
                    val referrerDoc = referrerQuery.documents.first()
                    val referrerId = referrerDoc.id
                    val referrerName = referrerDoc.getString("username") ?: "Unknown"

                    val teamMemberRef = db.collection("users")
                        .document(referrerId)
                        .collection("teamMembers")
                        .document(user.uid)

                    val teamMemberData = hashMapOf(
                        "joinDate" to com.google.firebase.Timestamp.now(),
                        "status" to "active",
                        "referralCode" to referralCode,
                        "addedAt" to com.google.firebase.Timestamp.now(),
                        "referrerName" to referrerName,
                        "rewardEarned" to REFERRAL_REWARD_POINTS,
                        "rewardProcessed" to true
                    )

                    batch.set(teamMemberRef, teamMemberData)

                    val referrerRef = db.collection("users").document(referrerId)
                    batch.update(referrerRef, mapOf(
                        "points" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong()),
                        "teamCount" to FieldValue.increment(1),
                        "totalReferralEarnings" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong())
                    ))

                    batch.update(userRef, mapOf(
                        "referredBy" to referrerId,
                        "referrerName" to referrerName
                    ))

                    val referrerTransactionRef = db.collection("users")
                        .document(referrerId)
                        .collection("transactions")
                        .document()

                    val referrerTransaction = hashMapOf(
                        "type" to "referral_reward",
                        "amount" to REFERRAL_REWARD_POINTS,
                        "description" to "Referral reward for inviting $username",
                        "referredUserId" to user.uid,
                        "referredUsername" to username,
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "status" to "completed"
                    )

                    batch.set(referrerTransactionRef, referrerTransaction)

                    if (REFERRAL_BONUS_POINTS > 0) {
                        val refereeTransactionRef = db.collection("users")
                            .document(user.uid)
                            .collection("transactions")
                            .document()

                        val refereeTransaction = hashMapOf(
                            "type" to "referral_bonus",
                            "amount" to REFERRAL_BONUS_POINTS,
                            "description" to "Welcome bonus for joining via referral",
                            "referrerId" to referrerId,
                            "referrerName" to referrerName,
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "status" to "completed"
                        )

                        batch.set(refereeTransactionRef, refereeTransaction)
                    }
                } else {
                    batch.update(userRef, "invalidReferralCode", referralCode)
                }
            }

            batch.commit().await()

            runOnUiThread {
                if (referralCode.isNotEmpty()) {
                    Toast.makeText(
                        this@SignUp,
                        "Account created! You received $REFERRAL_BONUS_POINTS welcome points!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@SignUp,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            NotificationWorkManager(this@SignUp).apply {
                scheduleMessageReminder(username)
                scheduleQuizReminders(username)
            }

        } catch (e: Exception) {
            when (e) {
                is FirebaseFirestoreException -> {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        throw Exception("Database permissions error. Please try again later.")
                    }
                }
            }
            throw e
        }
    }

    private fun createUserWallet(user: FirebaseUser) {
        val wallet = hashMapOf(
            "balance" to 0.0,
            "currency" to "USD"
        )

        db.collection("wallets").document(user.uid).set(wallet)
            .addOnSuccessListener {
                Toast.makeText(this, "Wallet initialized", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create wallet: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendFirebaseVerificationEmail(user: FirebaseUser) {
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                runOnUiThread {
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Verification email sent to ${user.email}",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToVerificationScreen(user.email)
                    } else {
                        handleSignUpFailure("Failed to send verification email: ${task.exception?.message}")
                    }
                }
            }
    }

    private fun navigateToVerificationScreen(email: String?) {
        val intent = Intent(this, VerifySignUp::class.java).apply {
            putExtra("email", email)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}