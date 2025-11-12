package com.koasac.tradeveil

import PrefsHelper
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)

        // Initialize PrefsHelper
        val prefsHelper = PrefsHelper(this)

        // Handler to delay transition
        Handler(Looper.getMainLooper()).postDelayed({
            when {
                prefsHelper.isFirstLaunch -> {
                    prefsHelper.isFirstLaunch = false
                    startActivity(Intent(this, GetStarted_Screen::class.java))
                }
                FirebaseAuth.getInstance().currentUser != null -> {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                else -> {
                    startActivity(Intent(this, SignUp::class.java))
                }
            }
            finish()
        }, 3000) // 3 second splash
    }
}