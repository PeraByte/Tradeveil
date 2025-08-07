package com.growtic.tradeveil

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManagePoints : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var pointsValue: TextView
    private lateinit var transferLimitValue: TextView
    private lateinit var userEmail: TextView
    private lateinit var requestButton: Button
    private lateinit var transferButton: Button
    private lateinit var backButton: ImageView
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var transactionsAdapter: TransactionsAdapter

    // listeners
    private var userListener: ListenerRegistration? = null
    private var sentListener: ListenerRegistration? = null
    private var recvListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "ManagePoints"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_points)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // TransferService is now managed by MyApplication, no need to start it here

        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        initViews()
        setupRecyclerView()
        listenToUser()
        setupTransferListeners()
        setupClickListeners()
    }

    private fun initViews() {
        pointsValue = findViewById(R.id.pointsValue)
        transferLimitValue = findViewById(R.id.transferLimitValue)
        userEmail = findViewById(R.id.userEmail)
        requestButton = findViewById(R.id.requestButton)
        transferButton = findViewById(R.id.transferButton)
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)

        // Set default text while loading
        userEmail.text = "Loading..."
        transferLimitValue.text = "5,000"
    }

    private fun setupRecyclerView() {
        transactionsAdapter = TransactionsAdapter(emptyList()) { transfer ->
            showTransferDetails(transfer)
        }
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionsRecyclerView.adapter = transactionsAdapter
    }

    private fun listenToUser() {
        val uid = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                if (snap == null || !snap.exists()) {
                    return@addSnapshotListener
                }

                try {
                    val points = snap.getLong("points") ?: 0
                    val transferPoints = snap.getLong("transferPoints") ?: 0
                    val username = snap.getString("username") ?: auth.currentUser?.email ?: "Unknown"

                    pointsValue.text = (points + transferPoints).formatWithCommas()
                    userEmail.text = username
                } catch (e: Exception) {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Error processing user data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun setupTransferListeners() {
        val uid = auth.currentUser?.uid ?: return

        // Remove orderBy from listeners to avoid index issues
        sentListener = db.collection("transfers")
            .whereEqualTo("senderId", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                lifecycleScope.launch { loadAndMergeTransfers() }
            }

        recvListener = db.collection("transfers")
            .whereEqualTo("receiverId", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                lifecycleScope.launch { loadAndMergeTransfers() }
            }
    }

    private suspend fun loadAndMergeTransfers() {
        val uid = auth.currentUser?.uid ?: return
        try {
            // Load sent transfers
            val sentQuery = db.collection("transfers")
                .whereEqualTo("senderId", uid)
                .get()
                .await()

            val sent = sentQuery.documents.mapNotNull { doc ->
                try {
                    val transfer = doc.toObject(Transfer::class.java)
                    if (transfer != null && transfer.isValid()) {
                        TransactionItem.SentTransfer(transfer)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            // Load received transfers
            val recvQuery = db.collection("transfers")
                .whereEqualTo("receiverId", uid)
                .get()
                .await()

            val recv = recvQuery.documents.mapNotNull { doc ->
                try {
                    val transfer = doc.toObject(Transfer::class.java)
                    if (transfer != null && transfer.isValid()) {
                        TransactionItem.ReceivedTransfer(transfer)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            // Merge and sort by timestamp
            val all = (sent + recv)
                .sortedByDescending { item ->
                    try {
                        when (item) {
                            is TransactionItem.SentTransfer -> item.transfer.timestamp ?: 0L
                            is TransactionItem.ReceivedTransfer -> item.transfer.timestamp ?: 0L
                        }
                    } catch (e: Exception) {
                        0L
                    }
                }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    transactionsAdapter.updateItems(all)
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Error loading transfers: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTransferDetails(transfer: Transfer) {
        val message = buildString {
            append("Transaction ID: ${transfer.transactionId}\n")
            append("Sender: ${transfer.senderEmail}\n")
            append("Receiver: ${transfer.receiverEmail}\n")
            append("Amount: ${transfer.getFormattedPoints()} points\n")
            if (transfer.fee > 0) {
                append("Fee: ${transfer.fee} points\n")
                append("Total Cost: ${transfer.getFormattedTotalCost()} points\n")
            }
            append("Date: ${transfer.getFormattedDate()}\n")
            append("Status: ${transfer.status.capitalize()}")
        }

        AlertDialog.Builder(this)
            .setTitle("Transfer Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy ID") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Transaction ID", transfer.transactionId))
                Toast.makeText(this, "Transaction ID copied", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setupClickListeners() {
        requestButton.setOnClickListener { showRequestDialog() }
        transferButton.setOnClickListener {
            startActivity(Intent(this, TransferPoints::class.java))
        }
    }

    private fun showRequestDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.receive_points_bottom_sheet, null)
        val emailTextView = dialogView.findViewById<TextView>(R.id.userEmail)
        val copyButton = dialogView.findViewById<ImageButton>(R.id.copyButton)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        val currentEmail = auth.currentUser?.email ?: ""
        emailTextView.text = currentEmail

        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(dialogView)

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Email", currentEmail))
            Toast.makeText(this, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        closeButton.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetDialog.show()
    }

    private fun Long.formatWithCommas(): String = "%,d".format(this)

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        sentListener?.remove()
        recvListener?.remove()
    }
}