package com.growwtic.tradeveil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class VerifySignUp : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var emailDisplay: TextView
    private lateinit var resendLinkBtn: Button
    private lateinit var openEmailBtn: Button
    private lateinit var db: FirebaseFirestore

    private val verificationCheckInterval = 5000L
    private val verificationCheckHandler = Handler()
    private lateinit var verificationCheckRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_sign_up)

        try {
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            emailDisplay = findViewById(R.id.email_display)
            resendLinkBtn = findViewById(R.id.resend_link_btn_vsu)
            openEmailBtn = findViewById(R.id.open_email_btn_vsu)

            val email = intent.getStringExtra("email") ?: ""
            emailDisplay.text = email

            openEmailBtn.setOnClickListener {
                openEmailClient(email)
            }

            resendLinkBtn.setOnClickListener {
                resendVerificationEmail()
            }

            verificationCheckRunnable = object : Runnable {
                override fun run() {
                    if (isFinishing || isDestroyed) {
                        return
                    }

                    val user = auth.currentUser
                    if (user == null) {
                        return
                    }

                    user.reload().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            if (user.isEmailVerified) {
                                completeSignUp(user)
                            } else {
                                verificationCheckHandler.postDelayed(this, verificationCheckInterval)
                            }
                        } else {
                            verificationCheckHandler.postDelayed(this, verificationCheckInterval)
                        }
                    }
                }
            }

            verificationCheckHandler.post(verificationCheckRunnable)
        } catch (e: Exception) {
            Toast.makeText(this, "Initialization error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEmailClient(email: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open mail link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun completeSignUp(user: FirebaseUser) {
        runOnUiThread {
            try {
                val email = user.email
                if (email.isNullOrEmpty()) {
                    Toast.makeText(
                        this,
                        "Error: No email associated with account",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@runOnUiThread
                }

                val userData = mapOf(
                    "email" to email,
                    "verified" to true,
                    "lastVerified" to FieldValue.serverTimestamp()
                )

                db.collection("users").document(user.uid)
                    .update(userData)
                    .addOnSuccessListener {
                        navigateToMainActivity()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Error updating verification status: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } catch (e: Exception) {
                // Handle exception silently
            }
        }
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(
                this,
                "No user found to resend verification",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                val message = if (task.isSuccessful) {
                    "Verification link sent to ${user.email}"
                } else {
                    "Failed to send verification email: ${task.exception?.message}"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToMainActivity() {
        if (!isFinishing && !isDestroyed) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        verificationCheckHandler.removeCallbacks(verificationCheckRunnable)
        super.onDestroy()
    }
}