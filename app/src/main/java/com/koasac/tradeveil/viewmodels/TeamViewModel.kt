package com.koasac.tradeveil.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koasac.tradeveil.data.TeamMember
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    companion object {
        const val REFERRAL_REWARD_POINTS = 20
        const val REFERRAL_BONUS_POINTS = 10 // Optional bonus for referee
    }

    private val _teamMembers = MutableLiveData<List<TeamMember>>()
    val teamMembers: LiveData<List<TeamMember>> = _teamMembers

    private val _userReferralCode = MutableLiveData<String?>()
    val userReferralCode: LiveData<String?> = _userReferralCode

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _teamStats = MutableLiveData<TeamStats>()
    val teamStats: LiveData<TeamStats> = _teamStats

    private var teamListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null

    data class TeamStats(
        val totalMembers: Int,
        val totalRewards: Int,
        val totalEarnings: Int
    )

    init {
        loadTeamData()
    }

    fun loadTeamData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _error.value = "User not authenticated"
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        _error.value = null

        // Load user's referral code
        userListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _error.value = "Error loading user data: ${error.message}"
                    return@addSnapshotListener
                }

                val referralCode = snapshot?.getString("referralCode")
                _userReferralCode.value = referralCode
            }

        // Load team members with enhanced debugging
        val teamMembersPath = "users/$userId/teamMembers"

        teamListener = db.collection("users")
            .document(userId)
            .collection("teamMembers")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    _isLoading.value = false
                    _error.value = "Error loading team data: ${error.message}"
                    _teamMembers.value = emptyList()
                    return@addSnapshotListener
                }



                if (snapshots == null) {
                    _isLoading.value = false
                    _teamMembers.value = emptyList()
                    return@addSnapshotListener
                }

                if (snapshots.isEmpty) {
                    _isLoading.value = false
                    _teamMembers.value = emptyList()
                    _teamStats.value = TeamStats(0, 0, 0)
                    return@addSnapshotListener
                }

                // Process team members in coroutine
                viewModelScope.launch {
                    try {
                        val members = mutableListOf<TeamMember>()
                        var totalEarnings = 0

                        for (doc in snapshots.documents) {
                            try {
                                val referredUserId = doc.id

                                // Get the team member document data
                                val joinDate = doc.getDate("joinDate") ?: Date()
                                val status = doc.getString("status") ?: "unknown"
                                val rewardEarned = doc.getLong("rewardEarned")?.toInt() ?: REFERRAL_REWARD_POINTS


                                // Get user details
                                val userDoc = db.collection("users")
                                    .document(referredUserId)
                                    .get()
                                    .await()

                                if (userDoc.exists()) {
                                    val username = userDoc.getString("username") ?: "Anonymous"
                                    val profileImageUrl = userDoc.getString("profileImageUrl") ?: ""
                                    val points = userDoc.getLong("points")?.toInt() ?: 0


                                    val member = TeamMember(
                                        userId = referredUserId,
                                        username = username,
                                        profileImageUrl = profileImageUrl,
                                        points = points,
                                        joinDate = joinDate
                                    )
                                    members.add(member)
                                    totalEarnings += rewardEarned
                                } else {

                                }
                            } catch (e: Exception) {
                            }
                        }

                        // Sort by join date (newest first)
                        val sortedMembers = members.sortedByDescending { it.joinDate }

                        sortedMembers.forEach { member ->
                        }

                        _teamMembers.value = sortedMembers
                        _teamStats.value = TeamStats(
                            totalMembers = sortedMembers.size,
                            totalRewards = sortedMembers.size * REFERRAL_REWARD_POINTS,
                            totalEarnings = totalEarnings
                        )
                        _isLoading.value = false

                    } catch (e: Exception) {
                        _error.value = "Error processing team data: ${e.message}"
                        _teamMembers.value = emptyList()
                        _isLoading.value = false
                    }
                }
            }
    }

    // INSTANT referral processing with point rewards
    fun processReferral(referralCode: String, newUserId: String) {
        viewModelScope.launch {
            try {

                // Find the referrer by their referral code
                val codeDoc = db.collection("referralCodes")
                    .document(referralCode)
                    .get()
                    .await()

                if (!codeDoc.exists()) {
                    _error.value = "Invalid referral code"
                    return@launch
                }

                val referrerId = codeDoc.getString("userId")
                if (referrerId == null) {
                    _error.value = "Invalid referral code format"
                    return@launch
                }


                // Prevent self-referral
                if (referrerId == newUserId) {
                    _error.value = "Cannot refer yourself"
                    return@launch
                }

                // Check if this user is already in the team
                val existingMember = db.collection("users")
                    .document(referrerId)
                    .collection("teamMembers")
                    .document(newUserId)
                    .get()
                    .await()

                if (existingMember.exists()) {
                    _error.value = "User is already in your team"
                    return@launch
                }

                val batch = db.batch()

                // Add to referrer's team members collection with reward info
                val teamMemberRef = db.collection("users")
                    .document(referrerId)
                    .collection("teamMembers")
                    .document(newUserId)

                val teamMemberData = mapOf(
                    "joinDate" to com.google.firebase.Timestamp.now(),
                    "status" to "active",
                    "referralCode" to referralCode,
                    "addedAt" to com.google.firebase.Timestamp.now(),
                    "rewardEarned" to REFERRAL_REWARD_POINTS,
                    "rewardProcessed" to true
                )

                batch.set(teamMemberRef, teamMemberData)

                // Award points to referrer (20 points per successful referral)
                val referrerRef = db.collection("users").document(referrerId)
                batch.update(referrerRef, mapOf(
                    "points" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong()),
                    "teamCount" to FieldValue.increment(1),
                    "totalReferralEarnings" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong())
                ))

                // Optional: Give bonus points to the new user (referee)
                val newUserRef = db.collection("users").document(newUserId)
                batch.update(newUserRef, mapOf(
                    "referredBy" to referrerId,
                    "referralCode" to referralCode,
                    "joinedViaReferral" to true,
                    "points" to FieldValue.increment(REFERRAL_BONUS_POINTS.toLong()), // Bonus for joining
                    "referralBonusReceived" to REFERRAL_BONUS_POINTS
                ))

                // Create transaction record for referrer
                val referrerTransactionRef = db.collection("users")
                    .document(referrerId)
                    .collection("transactions")
                    .document()

                val referrerTransaction = mapOf(
                    "type" to "referral_reward",
                    "amount" to REFERRAL_REWARD_POINTS,
                    "description" to "Referral reward for inviting user",
                    "referredUserId" to newUserId,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to "completed"
                )

                batch.set(referrerTransactionRef, referrerTransaction)

                // Create transaction record for referee (bonus)
                val refereeTransactionRef = db.collection("users")
                    .document(newUserId)
                    .collection("transactions")
                    .document()

                val refereeTransaction = mapOf(
                    "type" to "referral_bonus",
                    "amount" to REFERRAL_BONUS_POINTS,
                    "description" to "Welcome bonus for joining via referral",
                    "referrerId" to referrerId,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to "completed"
                )

                batch.set(refereeTransactionRef, refereeTransaction)

                batch.commit().await()



                // If the current user is the referrer, refresh the team data
                if (referrerId == auth.currentUser?.uid) {
                }

            } catch (e: Exception) {
                _error.value = "Error processing referral: ${e.message}"
            }
        }
    }

    fun addTeamMember(referredUserId: String) {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                _error.value = "User not authenticated"
                return@launch
            }

            try {

                // Check if already exists
                val existingMember = db.collection("users")
                    .document(userId)
                    .collection("teamMembers")
                    .document(referredUserId)
                    .get()
                    .await()

                if (existingMember.exists()) {
                    _error.value = "Team member already exists"
                    return@launch
                }

                val batch = db.batch()

                // Add to team members with reward info
                val teamMemberRef = db.collection("users")
                    .document(userId)
                    .collection("teamMembers")
                    .document(referredUserId)

                val memberData = mapOf(
                    "joinDate" to com.google.firebase.Timestamp.now(),
                    "status" to "active",
                    "addedManually" to true,
                    "rewardEarned" to REFERRAL_REWARD_POINTS,
                    "rewardProcessed" to true
                )

                batch.set(teamMemberRef, memberData)

                // Award points to current user and update team count
                val userRef = db.collection("users").document(userId)
                batch.update(userRef, mapOf(
                    "points" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong()),
                    "teamCount" to FieldValue.increment(1),
                    "totalReferralEarnings" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong())
                ))

                // Create transaction record
                val transactionRef = db.collection("users")
                    .document(userId)
                    .collection("transactions")
                    .document()

                val transaction = mapOf(
                    "type" to "manual_referral_reward",
                    "amount" to REFERRAL_REWARD_POINTS,
                    "description" to "Manual referral reward",
                    "referredUserId" to referredUserId,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to "completed"
                )

                batch.set(transactionRef, transaction)

                batch.commit().await()


            } catch (e: Exception) {
                _error.value = "Error adding team member: ${e.message}"
            }
        }
    }

    private suspend fun updateTeamCount(userId: String) {
        try {
            val count = db.collection("users")
                .document(userId)
                .collection("teamMembers")
                .get()
                .await()
                .size()

            db.collection("users")
                .document(userId)
                .update("teamCount", count)
                .await()

        } catch (e: Exception) {
        }
    }

    fun refreshTeamData() {
        loadTeamData()
    }

    fun clearError() {
        _error.value = null
    }

    // Debug function to manually check team members
    fun debugCheckTeamMembers() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {

                val snapshot = db.collection("users")
                    .document(userId)
                    .collection("teamMembers")
                    .get()
                    .await()


                snapshot.documents.forEach { doc ->
                }

            } catch (e: Exception) {
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        teamListener?.remove()
        userListener?.remove()
    }
}