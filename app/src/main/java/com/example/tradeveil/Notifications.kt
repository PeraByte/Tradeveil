package com.example.tradeveil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tradeveil.databinding.ActivityNotificationsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*

class Notifications : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationsBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val notifications = mutableListOf<Notification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the back button
        binding.backButton.setOnClickListener { finish() }

        // Set up RecyclerView
        binding.notificationRV.layoutManager = LinearLayoutManager(this)
        binding.notificationRV.adapter = NotificationAdapter(notifications)

        loadNotifications()
    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle error
                    return@addSnapshotListener
                }

                notifications.clear()
                snapshot?.documents?.forEach { document ->
                    val notification = document.toObject(Notification::class.java)
                    notification?.let {
                        notifications.add(it.copy(id = document.id))
                    }
                }
                binding.notificationRV.adapter?.notifyDataSetChanged()
            }
    }
}