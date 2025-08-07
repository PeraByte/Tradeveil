package com.growtic.tradeveil.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.growtic.tradeveil.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

class LeaderboardViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _leaderboardUsers = MutableLiveData<List<User>>()
    private val _topUsers = MutableLiveData<List<User>>()
    private val _isLoading = MutableLiveData<Boolean>()
    private val _errorMessage = MutableLiveData<String?>()

    val leaderboardUsers: LiveData<List<User>> get() = _leaderboardUsers
    val topUsers: LiveData<List<User>> get() = _topUsers
    val isLoading: LiveData<Boolean> get() = _isLoading
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun loadLeaderboard() {
        _isLoading.value = true
        _errorMessage.value = null

        db.collection("users")
            .orderBy("points", Query.Direction.DESCENDING)
            .limit(50) // Limit to top 50 users
            .get()
            .addOnSuccessListener { documents ->
                try {
                    val users = mutableListOf<User>()

                    for ((index, document) in documents.withIndex()) {
                        try {
                            // Manual parsing to avoid deserialization issues
                            val user = User(
                                id = document.id,
                                username = document.getString("username") ?: "",
                                email = document.getString("email") ?: "",
                                profileImageUrl = document.getString("profileImageUrl") ?: "",
                                totalProfit = document.getDouble("totalProfit") ?: 0.0,
                                totalTrades = document.getLong("totalTrades")?.toInt() ?: 0,
                                winRate = document.getDouble("winRate") ?: 0.0,
                                points = document.getLong("points") ?: 0L,
                                rank = index + 1, // Set the rank based on position in sorted list
                                level = document.getLong("level") ?: 1L,
                                checkInStreak = document.getLong("checkInStreak") ?: 0L,
                                teamCount = document.getLong("teamCount") ?: 0L,
                                totalCheckinPoints = document.getLong("totalCheckinPoints") ?: 0L,
                                lastAdWatchTime = document.getLong("lastAdWatchTime") ?: 0L,
                                lastMessageResetTime = document.getLong("lastMessageResetTime") ?: 0L
                            )
                            users.add(user)
                        } catch (e: Exception) {
                            // Skip this user and continue
                        }
                    }

                    // Update each user's rank in Firestore for future use
                    updateUserRanksInFirestore(users)

                    _leaderboardUsers.value = users
                    _topUsers.value = users.take(3)
                    _isLoading.value = false

                } catch (e: Exception) {
                    _errorMessage.value = "Error loading leaderboard: ${e.message}"
                    _isLoading.value = false
                }
            }
            .addOnFailureListener { exception ->
                _errorMessage.value = "Failed to load leaderboard. Please try again."
                _isLoading.value = false
            }
    }

    private fun updateUserRanksInFirestore(users: List<User>) {
        // Update ranks in Firestore in background
        CoroutineScope(Dispatchers.IO).launch {
            val batch = db.batch()

            users.forEach { user ->
                val userRef = db.collection("users").document(user.id)
                batch.update(userRef, "rank", user.rank)
            }

            batch.commit()
                .addOnSuccessListener {
                }
                .addOnFailureListener { exception ->
                }
        }
    }
}