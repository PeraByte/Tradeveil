package com.koasac.tradeveil

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class GlobalChat : AppCompatActivity() {

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var backBtn: ImageView
    private lateinit var creditsDisplay: TextView
    private lateinit var messagesAdapter: MessagesAdapter
    private var listenerRegistration: ListenerRegistration? = null
    private val messagesList = mutableListOf<ChatMessage>()
    private var rewardedAd: RewardedAd? = null
    private var messageCredits = 0
    private var chatPointsEarned = 0 // FIXED: Consistent naming
    private var lastMessageResetTime = 0L
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val MESSAGE_LIMIT = 10
    private val REWARD_CREDITS = 3
    private var adsWatched = 0
    private var lastAdWatchTime = 0L
    private val MAX_ADS_PER_PERIOD = 2
    private val AD_COOLDOWN_HOURS = 4
    private var isLoadingMessages = false
    private var isActivityDestroyed = false // ADDED: Activity lifecycle flag
    private val handler = Handler(Looper.getMainLooper())
    private val processedMessageIds = mutableSetOf<String>() // ADDED: Prevent duplicates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_chat)

        initializeViews()
        setupRecyclerView()
        loadUserData()
        loadRewardAd()
        setupClickListeners()
    }

    private fun initializeViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        backBtn = findViewById(R.id.backBtn)
        // Add this if you have a credits display in your layout
        // creditsDisplay = findViewById(R.id.creditsDisplay)

        // Check if user is authenticated
        if (auth.currentUser == null) {
            Toast.makeText(this, "Please log in to access chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun setupClickListeners() {
        // Back button functionality
        backBtn.setOnClickListener {
            finish()
        }

        sendButton.setOnClickListener {
            if (canSendMessage()) {
                sendMessage()
            } else {
                showNoCreditsDialog()
            }
        }

        // Allow sending message with Enter key
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                if (canSendMessage()) {
                    sendMessage()
                } else {
                    showNoCreditsDialog()
                }
                true
            } else {
                false
            }
        }

        // ADDED: Click on message input when no credits to show dialog
        messageInput.setOnClickListener {
            if (!canSendMessage()) {
                showNoCreditsDialog()
            }
        }
    }

    private fun loadUserData() {
        if (isActivityDestroyed) return

        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (isActivityDestroyed) return@addOnSuccessListener

                messageCredits = document.getLong("messageCredits")?.toInt() ?: MESSAGE_LIMIT
                chatPointsEarned = document.getLong("chatPointsEarned")?.toInt() ?: 0 // FIXED: Consistent naming
                lastMessageResetTime = document.getLong("lastMessageResetTime") ?: System.currentTimeMillis()
                adsWatched = document.getLong("adsWatched")?.toInt() ?: 0
                lastAdWatchTime = document.getLong("lastAdWatchTime") ?: 0L

                checkMessageReset()
                checkAdLimitReset()
                updateUI()
                loadMessages()
            }
            .addOnFailureListener { e ->
                if (isActivityDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
                // Set defaults and still load messages
                messageCredits = MESSAGE_LIMIT
                chatPointsEarned = 0
                loadMessages()
            }
    }

    private fun checkAdLimitReset() {
        val currentTime = System.currentTimeMillis()
        val cooldownMillis = AD_COOLDOWN_HOURS * 60 * 60 * 1000L

        if (currentTime - lastAdWatchTime > cooldownMillis) {
            adsWatched = 0
            lastAdWatchTime = currentTime
            saveUserData()
        }
    }

    private fun canWatchAd(): Boolean {
        return adsWatched < MAX_ADS_PER_PERIOD
    }

    private fun checkMessageReset() {
        val currentTime = System.currentTimeMillis()
        val twentyFourHours = 24 * 60 * 60 * 1000L

        if (currentTime - lastMessageResetTime > twentyFourHours) {
            messageCredits = MESSAGE_LIMIT
            lastMessageResetTime = currentTime
            saveUserData()
        }
    }

    private fun updateUI() {
        if (isActivityDestroyed) return // ADDED: Safety check

        updateSendButtonState()
        updateCreditsDisplay()
    }

    private fun updateSendButtonState() {
        if (isActivityDestroyed) return

        val canSend = canSendMessage()
        sendButton.isEnabled = canSend
        sendButton.alpha = if (canSend) 1.0f else 0.5f

        messageInput.hint = if (canSend) {
            "Type a message..."
        } else {
            "No messages left (tap to watch ad)"
        }
    }

    private fun updateCreditsDisplay() {
        if (isActivityDestroyed) return
        // Update this if you have a credits display in your layout
        // creditsDisplay?.text = "Credits: $messageCredits"
    }

    private fun canSendMessage(): Boolean {
        return messageCredits > 0
    }

    private fun loadRewardAd() {
        if (isActivityDestroyed) return

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-2219058417636032/4263774484",
            adRequest, object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    if (isActivityDestroyed) return
                    rewardedAd = null
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    if (isActivityDestroyed) return
                    rewardedAd = ad
                }
            })
    }

    private fun showNoCreditsDialog() {
        if (isActivityDestroyed) return

        if (!canWatchAd()) {
            val hoursLeft = ((AD_COOLDOWN_HOURS * 60 * 60 * 1000L) - (System.currentTimeMillis() - lastAdWatchTime)) / (60 * 60 * 1000L)
            Toast.makeText(this, "Ad limit reached. Try again in ${hoursLeft + 1} hours", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("No Message Credits Left")
            .setMessage("Watch an ad to get $REWARD_CREDITS more message credits?\n\nRemaining ads: ${MAX_ADS_PER_PERIOD - adsWatched}")
            .setPositiveButton("Watch Ad") { dialog, _ ->
                showRewardAd()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showRewardAd() {
        if (isActivityDestroyed) return

        if (!canWatchAd()) {
            Toast.makeText(this, "Ad limit reached for this period", Toast.LENGTH_LONG).show()
            return
        }

        rewardedAd?.let { ad ->
            ad.show(this) { rewardItem ->
                if (isActivityDestroyed) return@show // ADDED: Safety check

                adsWatched++
                lastAdWatchTime = System.currentTimeMillis()
                messageCredits += REWARD_CREDITS
                saveUserData()
                updateUI()

                val remainingAds = MAX_ADS_PER_PERIOD - adsWatched
                val message = "You earned $REWARD_CREDITS message credits!" +
                        if (remainingAds > 0) " ($remainingAds ads remaining)" else ""

                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                // Preload next ad
                loadRewardAd()
            }
        } ?: run {
            Toast.makeText(this, "Ad not ready yet, please try again", Toast.LENGTH_SHORT).show()
            loadRewardAd()
        }
    }

    private fun saveUserData() {
        if (isActivityDestroyed) return

        val userId = auth.currentUser?.uid ?: return

        val userData = hashMapOf(
            "messageCredits" to messageCredits,
            "chatPointsEarned" to chatPointsEarned, // FIXED: Consistent naming
            "lastMessageResetTime" to lastMessageResetTime,
            "adsWatched" to adsWatched,
            "lastAdWatchTime" to lastAdWatchTime
        )

        db.collection("users").document(userId).set(userData, SetOptions.merge())
            .addOnFailureListener { e ->
                if (isActivityDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter(messagesList)
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@GlobalChat).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
        }
    }

    // IMPROVED: Better message loading with proper DocumentChange handling
    private fun loadMessages() {
        if (isLoadingMessages || isActivityDestroyed) return
        isLoadingMessages = true

        listenerRegistration?.remove()

        // First load: get existing messages
        if (messagesList.isEmpty()) {
            db.collection("global_chat")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (isActivityDestroyed) return@addOnSuccessListener

                    messagesList.clear()
                    processedMessageIds.clear()

                    snapshot.documents.forEach { doc ->
                        val message = doc.toObject(ChatMessage::class.java)
                        message?.let {
                            messagesList.add(it)
                            processedMessageIds.add(doc.id)
                        }
                    }

                    messagesAdapter.notifyDataSetChanged()
                    scrollToBottom()

                    // Start real-time listener for new messages
                    startRealtimeListener()
                    isLoadingMessages = false
                }
                .addOnFailureListener { e ->
                    if (isActivityDestroyed) return@addOnFailureListener
                    Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoadingMessages = false
                }
        } else {
            startRealtimeListener()
            isLoadingMessages = false
        }
    }

    private fun startRealtimeListener() {
        if (isActivityDestroyed) return

        listenerRegistration = db.collection("global_chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || isActivityDestroyed) return@addSnapshotListener

                snapshot?.documentChanges?.forEach { change ->
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            val message = change.document.toObject(ChatMessage::class.java)
                            val docId = change.document.id

                            // Prevent duplicates
                            if (!processedMessageIds.contains(docId)) {
                                messagesList.add(message)
                                processedMessageIds.add(docId)
                                messagesAdapter.notifyItemInserted(messagesList.size - 1)
                                scrollToBottom()
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            // Handle message updates if needed
                            val message = change.document.toObject(ChatMessage::class.java)
                            val docId = change.document.id
                            val index = messagesList.indexOfFirst {
                                it.senderId == message.senderId &&
                                        it.timestamp == message.timestamp
                            }
                            if (index != -1) {
                                messagesList[index] = message
                                messagesAdapter.notifyItemChanged(index)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            // Handle message deletion if needed
                            val message = change.document.toObject(ChatMessage::class.java)
                            val docId = change.document.id
                            val index = messagesList.indexOfFirst {
                                it.senderId == message.senderId &&
                                        it.timestamp == message.timestamp
                            }
                            if (index != -1) {
                                messagesList.removeAt(index)
                                processedMessageIds.remove(docId)
                                messagesAdapter.notifyItemRemoved(index)
                            }
                        }
                    }
                }
            }
    }

    private fun scrollToBottom() {
        if (isActivityDestroyed || messagesList.isEmpty()) return

        handler.postDelayed({
            if (!isActivityDestroyed) {
                messagesRecyclerView.smoothScrollToPosition(messagesList.size - 1)
            }
        }, 100)
    }

    private fun sendMessage() {
        if (isActivityDestroyed) return

        val messageText = messageInput.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        if (messageText.length > 500) {
            Toast.makeText(this, "Message too long (max 500 characters)", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Authentication error. Please log in again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!canSendMessage()) {
            showNoCreditsDialog()
            return
        }

        val message = ChatMessage(
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Anonymous",
            senderPhotoUrl = currentUser.photoUrl?.toString() ?: "",
            messageText = messageText,
            timestamp = Timestamp.now()
        )

        // Disable send button temporarily
        sendButton.isEnabled = false

        db.collection("global_chat")
            .add(message)
            .addOnSuccessListener {
                if (isActivityDestroyed) return@addOnSuccessListener

                messageInput.text.clear()
                messageCredits--

                // REWARD: Give user 1 point per message sent
                chatPointsEarned += 1

                // FIXED: Also add 1 point to user's main points balance
                val userId = currentUser.uid
                val userRef = db.collection("users").document(userId)

                // Use FieldValue.increment to safely add points
                userRef.update("points", FieldValue.increment(1))
                    .addOnSuccessListener {
                    }
                    .addOnFailureListener { e ->
                        // Still save other data even if points update fails
                    }

                saveUserData()
                updateUI()

                Toast.makeText(this, "Message sent! (+1 point earned, $messageCredits credits left)", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                if (isActivityDestroyed) return@addOnFailureListener

                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                sendButton.isEnabled = canSendMessage()
            }
    }

    override fun onDestroy() {
        isActivityDestroyed = true
        listenerRegistration?.remove()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        // Keep listener active but mark as paused for optimization
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when coming back
        if (!isLoadingMessages && !isActivityDestroyed) {
            loadUserData()
        }
    }

    data class ChatMessage(
        val senderId: String = "",
        val senderName: String = "",
        val senderPhotoUrl: String = "",
        val messageText: String = "",
        val timestamp: Timestamp = Timestamp.now()
    )

    inner class MessagesAdapter(private val messages: List<ChatMessage>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_MY_MESSAGE = 1
        private val VIEW_TYPE_OTHER_MESSAGE = 2

        override fun getItemViewType(position: Int): Int {
            val message = messages[position]
            return if (message.senderId == auth.currentUser?.uid) {
                VIEW_TYPE_MY_MESSAGE
            } else {
                VIEW_TYPE_OTHER_MESSAGE
            }
        }

        inner class MyMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val messageText: TextView = itemView.findViewById(R.id.messageText)
            val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        }

        inner class OtherMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val profileImage: CircleImageView = itemView.findViewById(R.id.profileImage)
            val senderName: TextView = itemView.findViewById(R.id.senderName)
            val messageText: TextView = itemView.findViewById(R.id.messageText)
            val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_MY_MESSAGE) {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_right, parent, false)
                MyMessageViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_left, parent, false)
                OtherMessageViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (isActivityDestroyed) return // ADDED: Safety check

            val message = messages[position]
            // CHANGED: Updated to use 24-hour format (HH:mm)
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            when (holder) {
                is MyMessageViewHolder -> {
                    holder.messageText.text = message.messageText
                    holder.messageTime.text = dateFormat.format(message.timestamp.toDate())
                }
                is OtherMessageViewHolder -> {
                    holder.senderName.text = message.senderName
                    holder.messageText.text = message.messageText
                    holder.messageTime.text = dateFormat.format(message.timestamp.toDate())

                    // Enhanced image loading with better error handling
                    if (message.senderPhotoUrl.isNotEmpty() && !isActivityDestroyed) {
                        try {
                            Glide.with(holder.profileImage.context)
                                .load(message.senderPhotoUrl)
                                .apply(RequestOptions.circleCropTransform())
                                .placeholder(R.drawable.avatar)
                                .error(R.drawable.avatar)
                                .into(holder.profileImage)
                        } catch (e: Exception) {
                            holder.profileImage.setImageResource(R.drawable.avatar)
                        }
                    } else {
                        holder.profileImage.setImageResource(R.drawable.avatar)
                    }
                }
            }
        }

        override fun getItemCount(): Int = messages.size
    }
}