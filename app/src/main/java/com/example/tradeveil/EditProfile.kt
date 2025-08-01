package com.example.tradeveil

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.math.min

class EditProfile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var currentUser: FirebaseUser

    private lateinit var backButton: ImageView
    private lateinit var profileImage: ImageView
    private lateinit var editProfileImageButton: ImageView
    private lateinit var usernameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button

    private var imageUri: Uri? = null
    private var compressedImageData: ByteArray? = null
    private val PERMISSION_REQUEST_CODE = 123
    private val MAX_IMAGE_SIZE = 1024 // Max width/height in pixels
    private val JPEG_QUALITY = 85 // JPEG compression quality (0-100)

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                imageUri = uri

                // Compress image immediately after selection
                compressImage(uri)

                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.avatar)
                    .error(R.drawable.avatar)
                    .circleCrop()
                    .into(profileImage)

                Toast.makeText(this, "Image selected and optimized", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load selected image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        currentUser = auth.currentUser!!

        // Initialize views
        backButton = findViewById(R.id.backButton)
        profileImage = findViewById(R.id.profileImage)
        editProfileImageButton = findViewById(R.id.editProfileImageButton)
        usernameInput = findViewById(R.id.usernameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)

        loadUserData()

        // Set up click listeners
        backButton.setOnClickListener { finish() }
        cancelButton.setOnClickListener { finish() }
        editProfileImageButton.setOnClickListener { selectProfileImage() }
        saveButton.setOnClickListener { updateProfile() }
    }

    private fun compressImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap != null) {
                // Calculate new dimensions maintaining aspect ratio
                val originalWidth = originalBitmap.width
                val originalHeight = originalBitmap.height
                val ratio = originalWidth.toFloat() / originalHeight.toFloat()

                val newWidth: Int
                val newHeight: Int

                if (originalWidth > originalHeight) {
                    newWidth = min(MAX_IMAGE_SIZE, originalWidth)
                    newHeight = (newWidth / ratio).toInt()
                } else {
                    newHeight = min(MAX_IMAGE_SIZE, originalHeight)
                    newWidth = (newHeight * ratio).toInt()
                }

                // Resize bitmap
                val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

                // Compress to JPEG byte array
                val outputStream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                compressedImageData = outputStream.toByteArray()

                // Clean up
                outputStream.close()
                originalBitmap.recycle()
                resizedBitmap.recycle()
            }
        } catch (e: FileNotFoundException) {

        }
    }

    private fun selectProfileImage() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), PERMISSION_REQUEST_CODE)
                } else {
                    getContent.launch("image/*")
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
                } else {
                    getContent.launch("image/*")
                }
            } else {
                getContent.launch("image/*")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error selecting image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getContent.launch("image/*")
            } else {
                Toast.makeText(this, "Permission denied. Cannot select image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserData() {
        // Load current profile image
        val storageRef = storage.reference
        val profileImageRef = storageRef.child("profile_images/${currentUser.uid}/profile.jpg")

        profileImageRef.downloadUrl
            .addOnSuccessListener { uri ->
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.avatar)
                    .error(R.drawable.avatar)
                    .circleCrop()
                    .into(profileImage)
            }
            .addOnFailureListener {
                profileImage.setImageResource(R.drawable.avatar)
            }

        // Load username from Firestore
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val username = document.getString("username")
                    usernameInput.setText(username ?: currentUser.displayName)
                } else {
                    usernameInput.setText(currentUser.displayName)
                }
            }
            .addOnFailureListener { e ->
                usernameInput.setText(currentUser.displayName)
            }

        emailInput.setText(currentUser.email)
    }

    private fun updateProfile() {
        val username = usernameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (TextUtils.isEmpty(username)) {
            usernameInput.error = "Username is required"
            return
        }

        // Update button appearance
        saveButton.isEnabled = false
        saveButton.text = "Loading..."
        saveButton.setTextColor(Color.GRAY)

        if (compressedImageData != null) {
            uploadProfileImage(username, email, password)
        } else {
            updateUsername(username, email, password)
        }
    }

    private fun uploadProfileImage(username: String, email: String, password: String) {
        val storageRef = storage.reference
        val imageRef = storageRef.child("profile_images/${currentUser.uid}/profile.jpg")

        // Create metadata for better caching and compression
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCacheControl("max-age=86400") // Cache for 24 hours
            .build()

        compressedImageData?.let { data ->
            imageRef.putBytes(data, metadata)
                .addOnProgressListener { taskSnapshot ->
                    // Optional: Show upload progress
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                }
                .addOnSuccessListener {

                    // Get download URL and save it to Firestore
                    imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        db.collection("users").document(currentUser.uid)
                            .update("profileImageUrl", downloadUri.toString())
                            .addOnSuccessListener {
                                updateUsername(username, email, password)
                            }
                            .addOnFailureListener { e ->
                                handleUpdateError(e)
                            }
                    }.addOnFailureListener { e ->
                        handleUpdateError(e)
                    }
                }
                .addOnFailureListener { e ->
                    handleUpdateError(e)
                }
        }
    }

    private fun updateUsername(username: String, email: String, password: String) {
        val updates = mutableMapOf<String, Any>("username" to username)

        db.collection("users").document(currentUser.uid)
            .update(updates)
            .addOnSuccessListener {
                updateEmailIfNeeded(email, password)
            }
            .addOnFailureListener { e ->
                handleUpdateError(e)
            }
    }

    private fun updateEmailIfNeeded(email: String, password: String) {
        if (email != currentUser.email && !TextUtils.isEmpty(email)) {
            if (TextUtils.isEmpty(password)) {
                passwordInput.error = "Password is required to change email"
                resetSaveButton()
                return
            }

            val credential = EmailAuthProvider.getCredential(currentUser.email!!, password)
            currentUser.reauthenticate(credential)
                .addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        currentUser.updateEmail(email)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    db.collection("users").document(currentUser.uid)
                                        .update("email", email)
                                        .addOnSuccessListener { completeUpdate() }
                                        .addOnFailureListener { e -> handleUpdateError(e) }
                                } else {
                                    handleUpdateError(task.exception)
                                }
                            }
                    } else {
                        handleUpdateError(authTask.exception)
                    }
                }
        } else {
            completeUpdate()
        }
    }

    private fun completeUpdate() {
        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun handleUpdateError(exception: Exception?) {
        resetSaveButton()
        Toast.makeText(this, "Failed to update profile: ${exception?.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
    }

    private fun resetSaveButton() {
        saveButton.isEnabled = true
        saveButton.text = "Save"
        saveButton.setTextColor(ContextCompat.getColor(this, R.color.white)) // Use your primary color
    }
}