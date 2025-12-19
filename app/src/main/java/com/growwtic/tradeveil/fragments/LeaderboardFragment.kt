package com.growwtic.tradeveil.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.growwtic.tradeveil.R
import com.growwtic.tradeveil.adapters.LeaderboardAdapter
import com.growwtic.tradeveil.databinding.FragmentLeaderboardBinding
import com.growwtic.tradeveil.viewmodels.LeaderboardViewModel
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: LeaderboardViewModel
    private lateinit var adapter: LeaderboardAdapter
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this).get(LeaderboardViewModel::class.java)
        setupRecyclerView()
        setupErrorHandling()
        observeViewModel()

        // Load leaderboard data
        viewModel.loadLeaderboard()

        // Setup retry button
        binding.retryButton.setOnClickListener {
            viewModel.loadLeaderboard()
        }
    }

    private fun setupRecyclerView() {
        adapter = LeaderboardAdapter(emptyList(), auth.currentUser?.uid ?: "") { userId ->
            // Handle profile image click if needed
        }
        binding.leaderboardRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@LeaderboardFragment.adapter
        }
    }

    private fun setupErrorHandling() {
        binding.errorContainer.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.topUsers.observe(viewLifecycleOwner) { topUsers ->

            // Reset podium visibility
            binding.firstPlaceCrown.visibility = View.GONE
            binding.secondPlaceCrown.visibility = View.GONE
            binding.thirdPlaceCrown.visibility = View.GONE

            when (topUsers.size) {
                3 -> {
                    loadProfileImageFromUrl(topUsers[0].profileImageUrl, binding.firstPlaceAvatar)
                    loadProfileImageFromUrl(topUsers[1].profileImageUrl, binding.secondPlaceAvatar)
                    loadProfileImageFromUrl(topUsers[2].profileImageUrl, binding.thirdPlaceAvatar)

                    binding.firstPlaceCrown.visibility = View.VISIBLE
                    binding.secondPlaceCrown.visibility = View.VISIBLE
                    binding.thirdPlaceCrown.visibility = View.VISIBLE
                }
                2 -> {
                    loadProfileImageFromUrl(topUsers[0].profileImageUrl, binding.firstPlaceAvatar)
                    loadProfileImageFromUrl(topUsers[1].profileImageUrl, binding.secondPlaceAvatar)

                    binding.firstPlaceCrown.visibility = View.VISIBLE
                    binding.secondPlaceCrown.visibility = View.VISIBLE
                }
                1 -> {
                    loadProfileImageFromUrl(topUsers[0].profileImageUrl, binding.firstPlaceAvatar)
                    binding.firstPlaceCrown.visibility = View.VISIBLE
                }
            }

        }

        viewModel.leaderboardUsers.observe(viewLifecycleOwner) { users ->
            if (users.isEmpty()) {
                showEmptyState()
            } else {
                hideErrorState()
                adapter.updateData(users)
                // Log user ranks for debugging
                users.take(5).forEach { user ->
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.errorContainer.visibility = View.GONE
                binding.emptyStateContainer.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showErrorState(it)
            }
        }
    }

    private fun loadProfileImageFromUrl(url: String?, imageView: ShapeableImageView) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .transform(CircleCrop())
                .placeholder(R.drawable.avatar)
                .error(R.drawable.avatar)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.avatar)
        }
    }

    private fun showErrorState(message: String) {
        binding.errorContainer.visibility = View.VISIBLE
        binding.errorMessage.text = message
        binding.leaderboardRecyclerView.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.GONE
    }

    private fun hideErrorState() {
        binding.errorContainer.visibility = View.GONE
        binding.leaderboardRecyclerView.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.leaderboardRecyclerView.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}