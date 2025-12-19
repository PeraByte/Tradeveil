package com.growwtic.tradeveil

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {
    private val TAG = "LoginActivity"

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var loginButton: Button
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signupRedirectText: TextView

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser?.let { user ->
            if (user.isEmailVerified) {
                // User is authenticated and verified
                navigateToMainActivity()
            } else {
                // User is authenticated but not verified
                handleUnverifiedUser(user)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check authentication state BEFORE setting content view
        checkAuthStateImmediately()
        setContentView(R.layout.activity_login)

        initializeFirebase()
        initializeViews()
        setupClickListeners()
    }

    override fun onStart() {
        super.onStart()
        // Add the auth state listener
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        // Remove the auth state listener
        auth.removeAuthStateListener(authStateListener)
    }

    private fun checkAuthStateImmediately() {
        auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { user ->
            if (user.isEmailVerified) {
                // User is already logged in and verified
                navigateToMainActivity()
                finish()
            } else {
                // User is logged in but not verified
                handleUnverifiedUser(user)
                finish()
            }
        }
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
    }

    private fun initializeViews() {
        loginButton = findViewById(R.id.login_continue_button)
        emailEditText = findViewById(R.id.login_email_input)
        passwordEditText = findViewById(R.id.login_password_input)
        signupRedirectText = findViewById(R.id.login_signup_text)
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            when {
                email.isEmpty() -> emailEditText.error = "Email is required"
                password.isEmpty() -> passwordEditText.error = "Password is required"
                else -> loginUser(email, password)
            }
        }

        signupRedirectText.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        setLoginButtonState(false, "Logging in...")

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    checkEmailVerification()
                } else {
                    setLoginButtonState(true, "Continue")
                    showToast("Login failed: ${task.exception?.message}")
                }
            }
    }

    private fun checkEmailVerification() {
        val user = auth.currentUser ?: run {
            setLoginButtonState(true, "Continue")
            showToast("User not found")
            return
        }

        user.reload().addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                if (user.isEmailVerified) {
                    checkFirestoreAccess()
                } else {
                    handleUnverifiedUser(user)
                }
            } else {
                setLoginButtonState(true, "Continue")
                showToast("Error checking verification: ${reloadTask.exception?.message}")
            }
        }
    }

    private fun checkFirestoreAccess() {
        db.collection("userTickets").document(auth.currentUser?.uid ?: "")
            .get()
            .addOnSuccessListener {
                navigateToMainActivity()
            }
            .addOnFailureListener { e ->
                showToast("Configuration error. Please try again later.")
                auth.signOut()
                setLoginButtonState(true, "Continue")
            }
    }

    private fun handleUnverifiedUser(user: FirebaseUser) {
        resendVerificationEmail(user)
        startActivity(Intent(this, VerifySignUp::class.java).apply {
            putExtra("email", user.email)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun resendVerificationEmail(user: FirebaseUser) {
        user.sendEmailVerification().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                showToast("Verification email resent to ${user.email}", Toast.LENGTH_LONG)
            } else {
                showToast("Failed to resend verification email: ${task.exception?.message}")
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this@Login, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish() // Important to prevent going back to login
    }

    private fun setLoginButtonState(enabled: Boolean, text: String) {
        loginButton.isEnabled = enabled
        loginButton.text = text
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }
}