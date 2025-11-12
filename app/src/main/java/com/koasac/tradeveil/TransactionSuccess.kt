package com.koasac.tradeveil

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import com.koasac.tradeveil.databinding.ActivityTransactionSuccessBinding

class TransactionSuccess : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityTransactionSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.content_transaction_success)

        // Get data from intent
        val pointsAmount = intent.getStringExtra("points_amount")
        val recipientEmail = intent.getStringExtra("recipient_email")
        val pointsWithFee = intent.getStringExtra("points_with_fee")

        // Set values to views
        findViewById<TextView>(R.id.amountFiat).text = "$pointsAmount Veils"
        findViewById<TextView>(R.id.recipientAddress).text =
            "You have successfully transferred points to $recipientEmail"

        // Set click listener for Done button
        findViewById<Button>(R.id.doneButton).setOnClickListener {
            finish() // Close this activity
        }
    }
}