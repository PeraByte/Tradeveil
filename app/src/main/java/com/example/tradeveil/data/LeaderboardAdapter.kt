package com.example.tradeveil.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.tradeveil.R
import com.example.tradeveil.models.User
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.storage.FirebaseStorage

class LeaderboardAdapter(
    private var users: List<User>,
    private val currentUserId: String,
    private val onProfileImageClick: (String) -> Unit
) : RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {


    inner class LeaderboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val positionTextView: TextView = itemView.findViewById(R.id.positionTextView)
        val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        val pointsTextView: TextView = itemView.findViewById(R.id.pointsTextView)
        val profileImageView: ShapeableImageView = itemView.findViewById(R.id.profileImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_view_leaderboard, parent, false)
        return LeaderboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val user = users[position]

        holder.positionTextView.text = user.rank.toString()
        holder.usernameTextView.text = user.username
        holder.pointsTextView.text = user.points.toString()

        // Highlight current user
        if (user.id == currentUserId) {
            holder.itemView.setBackgroundResource(R.drawable.current_user_leaderboard_bg)
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent)
        }

        // Load profile image
        loadProfileImage(user.profileImageUrl, holder.profileImageView)

        // Set click listener for profile image
        holder.profileImageView.setOnClickListener {
            onProfileImageClick(user.id)
        }
    }

    private fun loadProfileImage(profileImageUrl: String?, imageView: ShapeableImageView) {
        if (!profileImageUrl.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(profileImageUrl)
                .transform(CircleCrop())
                .placeholder(R.drawable.avatar)
                .error(R.drawable.avatar)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.avatar)
        }
    }


    override fun getItemCount() = users.size

    fun updateData(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }
}