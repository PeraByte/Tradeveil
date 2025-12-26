package com.growtiic.tradeveil.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class ReferralCheckService : Service() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var referralListener: ListenerRegistration? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startReferralCheck()
        return START_STICKY
    }

    private fun startReferralCheck() {
        referralListener = db.collectionGroup("pendingReferrals")
            .whereLessThan("timestamp", Calendar.getInstance().timeInMillis - 7 * 24 * 60 * 60 * 1000)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                snapshots?.forEach { doc ->
                    processPendingReferral(doc.reference)
                }
            }
    }

    private fun processPendingReferral(ref: DocumentReference) {
        db.runTransaction { transaction ->
            // Get referral data
            val referral = transaction.get(ref).toObject(Referral::class.java) ?: return@runTransaction

            // Get referrer data
            val referrerPath = ref.parent.parent?.path ?: return@runTransaction
            val referrerRef = db.document(referrerPath)
            val referrer = transaction.get(referrerRef).toObject(User::class.java) ?: return@runTransaction

            // Get referred user data
            val referredUserRef = db.collection("users").document(referral.referredUserId)
            val referredUser = transaction.get(referredUserRef).toObject(User::class.java) ?: return@runTransaction

            // Update points
            val points = referral.potentialPoints ?: 20
            transaction.update(referrerRef, "points", referrer.points + points)
            transaction.update(referredUserRef, "points", referredUser.points + points)

            // Update team count
            transaction.update(referrerRef, "teamCount", referrer.teamCount + 1)

            // Move to confirmed referrals
            val confirmedRef = referrerRef.collection("confirmedReferrals").document(referral.referredUserId)
            transaction.set(confirmedRef, referral.copy(status = "completed"))

            // Remove from pending
            transaction.delete(ref)
        }.addOnSuccessListener {
        }.addOnFailureListener { e ->
        }
    }

    override fun onDestroy() {
        referralListener?.remove()
        super.onDestroy()
    }

    data class Referral(
        val referredUserId: String = "",
        val timestamp: Long = 0,
        val status: String = "pending",
        val potentialPoints: Int = 20
    )

    data class User(
        val points: Int = 0,
        val teamCount: Int = 0
    )
}