package com.example.tradeveil.data

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tradeveil.R

class TeamAdapter(
    private val onItemClick: (TeamMember) -> Unit = {}
) : ListAdapter<TeamMember, TeamAdapter.TeamViewHolder>(TeamDiffCallback()) {

    inner class TeamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProfile: ImageView = itemView.findViewById(R.id.tvProfile)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvPoints: TextView = itemView.findViewById(R.id.tvPoint)

        fun bind(member: TeamMember) {
            // Load profile image (Glide handles null/empty URLs internally)
            Glide.with(itemView.context)
                .load(member.profileImageUrl.takeIf { !it.isNullOrBlank() })
                .placeholder(R.drawable.avatar)
                .error(R.drawable.avatar)
                .circleCrop()
                .into(tvProfile)

            // Set username with fallback
            tvUsername.text = member.username.takeIf { !it.isNullOrBlank() } ?: "Anonymous"

            // Format points (e.g., "1,000 pts")
            tvPoints.text = itemView.context.getString(R.string.points_format, member.points)

            // Click listener
            itemView.setOnClickListener { onItemClick(member) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_view_team, parent, false)
        return TeamViewHolder(view)
    }

    override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // DiffUtil callback for efficient list updates
    private class TeamDiffCallback : DiffUtil.ItemCallback<TeamMember>() {
        override fun areItemsTheSame(oldItem: TeamMember, newItem: TeamMember): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: TeamMember, newItem: TeamMember): Boolean {
            return oldItem == newItem
        }
    }
}