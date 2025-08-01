package com.example.tradeveil.utils

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await



object ReferralUtils {
    private val firestore = FirebaseFirestore.getInstance()

    // Reward constants
    const val REFERRAL_REWARD_POINTS = 20      // Points given to referrer
    const val REFERRAL_BONUS_POINTS = 10       // Points given to new user (referee)
    const val MILESTONE_BONUS_5_REFERRALS = 50 // Bonus for 5 referrals
    const val MILESTONE_BONUS_10_REFERRALS = 100 // Bonus for 10 referrals
    const val MILESTONE_BONUS_25_REFERRALS = 300 // Bonus for 25 referrals
    private const val MAX_GENERATION_ATTEMPTS = 5
    private const val CODE_PREFIX = "TRV"
    private const val FALLBACK_CODE_LENGTH = 6



    suspend fun awardReferralRewards(
        db: FirebaseFirestore,
        referrerId: String,
        refereeId: String,
        refereeUsername: String
    ): Boolean {
        return try {

            val batch = db.batch()

            // Award points to referrer
            val referrerRef = db.collection("users").document(referrerId)
            batch.update(referrerRef, mapOf(
                "points" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong()),
                "totalReferralEarnings" to FieldValue.increment(REFERRAL_REWARD_POINTS.toLong())
            ))

            // Award bonus points to referee
            val refereeRef = db.collection("users").document(refereeId)
            batch.update(refereeRef, mapOf(
                "points" to FieldValue.increment(REFERRAL_BONUS_POINTS.toLong()),
                "referralBonusReceived" to REFERRAL_BONUS_POINTS
            ))

            // Create transaction records
            createTransactionRecord(
                batch, db, referrerId,
                "referral_reward", REFERRAL_REWARD_POINTS,
                "Referral reward for inviting $refereeUsername"
            )

            createTransactionRecord(
                batch, db, refereeId,
                "referral_bonus", REFERRAL_BONUS_POINTS,
                "Welcome bonus for joining via referral"
            )

            batch.commit().await()

            // Check for milestone bonuses
            checkMilestoneBonuses(db, referrerId)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check and award milestone bonuses for reaching referral goals
     */
    private suspend fun checkMilestoneBonuses(db: FirebaseFirestore, userId: String) {
        try {
            // Get current team count
            val userDoc = db.collection("users").document(userId).get().await()
            val teamCount = userDoc.getLong("teamCount")?.toInt() ?: 0
            val milestonesAwarded = userDoc.get("milestonesAwarded") as? List<Int> ?: emptyList()


            val milestones = mapOf(
                5 to MILESTONE_BONUS_5_REFERRALS,
                10 to MILESTONE_BONUS_10_REFERRALS,
                25 to MILESTONE_BONUS_25_REFERRALS
            )

            for ((milestone, bonus) in milestones) {
                if (teamCount >= milestone && !milestonesAwarded.contains(milestone)) {

                    val batch = db.batch()
                    val userRef = db.collection("users").document(userId)

                    // Award milestone bonus
                    batch.update(userRef, mapOf(
                        "points" to FieldValue.increment(bonus.toLong()),
                        "totalReferralEarnings" to FieldValue.increment(bonus.toLong()),
                        "milestonesAwarded" to FieldValue.arrayUnion(milestone)
                    ))

                    // Create transaction record
                    createTransactionRecord(
                        batch, db, userId,
                        "milestone_bonus", bonus,
                        "Milestone bonus for $milestone referrals"
                    )

                    batch.commit().await()
                }
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Helper function to create transaction records
     */
    private fun createTransactionRecord(
        batch: com.google.firebase.firestore.WriteBatch,
        db: FirebaseFirestore,
        userId: String,
        type: String,
        amount: Int,
        description: String
    ) {
        val transactionRef = db.collection("users")
            .document(userId)
            .collection("transactions")
            .document()

        val transaction = mapOf(
            "type" to type,
            "amount" to amount,
            "description" to description,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "status" to "completed"
        )

        batch.set(transactionRef, transaction)
    }

    /**
     * Get user's total referral earnings
     */
    suspend fun getUserReferralEarnings(db: FirebaseFirestore, userId: String): Int {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            userDoc.getLong("totalReferralEarnings")?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get user's team stats including earnings breakdown
     */
    suspend fun getTeamStats(db: FirebaseFirestore, userId: String): TeamStats {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            val teamCount = userDoc.getLong("teamCount")?.toInt() ?: 0
            val totalEarnings = userDoc.getLong("totalReferralEarnings")?.toInt() ?: 0
            val milestonesAwarded = userDoc.get("milestonesAwarded") as? List<Int> ?: emptyList()

            TeamStats(
                totalMembers = teamCount,
                totalEarnings = totalEarnings,
                baseRewards = teamCount * REFERRAL_REWARD_POINTS,
                milestoneRewards = calculateMilestoneRewards(milestonesAwarded),
                milestonesAchieved = milestonesAwarded.size
            )
        } catch (e: Exception) {
            TeamStats(0, 0, 0, 0, 0)
        }
    }

    private fun calculateMilestoneRewards(milestonesAwarded: List<Int>): Int {
        var total = 0
        milestonesAwarded.forEach { milestone ->
            when (milestone) {
                5 -> total += MILESTONE_BONUS_5_REFERRALS
                10 -> total += MILESTONE_BONUS_10_REFERRALS
                25 -> total += MILESTONE_BONUS_25_REFERRALS
            }
        }
        return total
    }

    /**
     * Data class for team statistics
     */
    data class TeamStats(
        val totalMembers: Int,
        val totalEarnings: Int,
        val baseRewards: Int,
        val milestoneRewards: Int,
        val milestonesAchieved: Int
    )

    /**
     * Get next milestone info
     */
    fun getNextMilestoneInfo(currentTeamCount: Int): MilestoneInfo? {
        val milestones = listOf(5, 10, 25)
        val nextMilestone = milestones.find { it > currentTeamCount }

        return nextMilestone?.let { milestone ->
            val reward = when (milestone) {
                5 -> MILESTONE_BONUS_5_REFERRALS
                10 -> MILESTONE_BONUS_10_REFERRALS
                25 -> MILESTONE_BONUS_25_REFERRALS
                else -> 0
            }

            MilestoneInfo(
                referralsNeeded = milestone,
                remainingReferrals = milestone - currentTeamCount,
                bonusReward = reward
            )
        }
    }

    /**
     * Data class for milestone information
     */
    data class MilestoneInfo(
        val referralsNeeded: Int,
        val remainingReferrals: Int,
        val bonusReward: Int
    )

    suspend fun generateUniqueCode(userId: String): String {
        var attempts = 0
        var code: String

        do {
            code = generateRandomCode()
            val exists = firestore.collection("referralCodes")
                .document(code)
                .get()
                .await()
                .exists()

            if (attempts++ >= MAX_GENERATION_ATTEMPTS) {
                return generateFallbackCode(userId).also {
                }
            }
        } while (exists)

        return code
    }

    private fun generateRandomCode(): String {
        val letters = ('A'..'Z').shuffled().take(3).joinToString("")
        val numbers = ('0'..'9').shuffled().take(3).joinToString("")
        return "$CODE_PREFIX$letters$numbers"
    }

    private fun generateFallbackCode(userId: String): String {
        val cleanId = userId.replace("[^a-zA-Z0-9]".toRegex(), "")
        return if (cleanId.length >= FALLBACK_CODE_LENGTH) {
            "$CODE_PREFIX${cleanId.takeLast(FALLBACK_CODE_LENGTH).uppercase()}"
        } else {
            "$CODE_PREFIX${cleanId.padStart(FALLBACK_CODE_LENGTH, '0').uppercase()}"
        }
    }

    suspend fun validateReferralCode(code: String): Boolean {
        if (!isValidCodeFormat(code)) return false

        return try {
            val document = firestore.collection("referralCodes")
                .document(code)
                .get()
                .await()

            document.exists() && document.get("userId") != null
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidCodeFormat(code: String): Boolean {
        return code.startsWith(CODE_PREFIX) &&
                code.length == (CODE_PREFIX.length + 6) &&
                code.substring(CODE_PREFIX.length).matches("^[A-Z0-9]+\$".toRegex())
    }
}