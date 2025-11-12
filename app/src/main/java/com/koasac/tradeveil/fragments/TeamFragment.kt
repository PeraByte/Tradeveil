package com.koasac.tradeveil.fragments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import com.koasac.tradeveil.R
import com.koasac.tradeveil.data.TeamAdapter
import com.koasac.tradeveil.data.TeamMember
import com.koasac.tradeveil.databinding.FragmentTeamBinding
import com.koasac.tradeveil.viewmodels.TeamViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TeamFragment : Fragment() {

    private var _binding: FragmentTeamBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TeamViewModel by viewModels()
    private lateinit var teamAdapter: TeamAdapter

    // Store original list for search functionality
    private var allTeamMembers: List<TeamMember> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupReferralCodeView()
        setupSwipeRefresh()
        setupSearchBar()

        // Load data after setup is complete
        loadData()
    }

    private fun setupRecyclerView() {
        teamAdapter = TeamAdapter { member ->
            // Handle item click - show member info with reward details
            val rewardInfo = "Earned ${TeamViewModel.REFERRAL_REWARD_POINTS} points for this referral"
            showToast("${member.username} - ${member.points} points\n$rewardInfo")
        }

        binding.rvTeamMembers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = teamAdapter
            setHasFixedSize(true)

            // Add divider between items
            val dividerItemDecoration = DividerItemDecoration(
                requireContext(),
                LinearLayoutManager.VERTICAL
            )
            addItemDecoration(dividerItemDecoration)
        }
    }

    private fun setupObservers() {
        // Observe team members - now using ListAdapter's submitList
        viewModel.teamMembers.observe(viewLifecycleOwner) { members ->
            allTeamMembers = members // Store original list for search

            // Apply current search filter if any
            val currentQuery = binding.searchBar.text.toString().trim()
            if (currentQuery.isEmpty()) {
                teamAdapter.submitList(members)
            } else {
                filterTeamMembers(currentQuery)
            }

            updateEmptyState(members.isEmpty())
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        // Observe errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showSnackbar(it, isError = true)
                viewModel.clearError()
            }
        }

        // Observe team stats for rewards display
        viewModel.teamStats.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                updateTeamStatsDisplay(it)
            }
        }
    }

    private fun setupSearchBar() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                filterTeamMembers(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Optional: Add search icon click listener for additional functionality
        binding.searchBar.setOnEditorActionListener { _, _, _ ->
            // Hide keyboard when search is pressed
            binding.searchBar.clearFocus()
            true
        }
    }

    private fun filterTeamMembers(query: String) {
        if (query.isEmpty()) {
            // Show all members if query is empty
            teamAdapter.submitList(allTeamMembers)
        } else {
            // Filter members based on username or user ID
            val filteredList = allTeamMembers.filter { member ->
                member.username.contains(query, ignoreCase = true) ||
                        member.userId.contains(query, ignoreCase = true)
            }

            teamAdapter.submitList(filteredList)

            // Show message if no results found
            if (filteredList.isEmpty() && allTeamMembers.isNotEmpty()) {
                showSnackbar("No team members found matching '$query'")
            }
        }

        // Update empty state based on filtered results
        updateEmptyState(teamAdapter.currentList.isEmpty())
    }

    private fun setupReferralCodeView() {
        viewModel.userReferralCode.observe(viewLifecycleOwner) { code ->
            binding.tvReferralCode.text = getString(R.string.your_referral_code, code ?: "Loading...")
            updateCodeActionsVisibility(code != null)
        }

        binding.btnCopyCode.setOnClickListener {
            viewModel.userReferralCode.value?.let { code ->
                if (copyToClipboard(code)) {
                    showSnackbar(getString(R.string.referral_code_copied))
                } else {
                    showSnackbar(getString(R.string.copy_failed), isError = true)
                }
            } ?: showSnackbar(getString(R.string.no_referral_code), isError = true)
        }

        binding.btnShareCode.setOnClickListener {
            viewModel.userReferralCode.value?.let { code ->
                shareReferralCode(code)
            } ?: showSnackbar(getString(R.string.no_referral_code), isError = true)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.apply {
            setOnRefreshListener {
                viewModel.refreshTeamData()
            }

            // Customize refresh colors
            setColorSchemeResources(
                R.color.blue_btn,
                R.color.blue_btn,
                R.color.blue_btn
            )

            // Set background color
            setProgressBackgroundColorSchemeResource(android.R.color.white)
        }
    }

    private fun updateCodeActionsVisibility(hasCode: Boolean) {
        binding.btnCopyCode.isEnabled = hasCode
        binding.btnShareCode.isEnabled = hasCode
        binding.btnCopyCode.alpha = if (hasCode) 1f else 0.5f
        binding.btnShareCode.alpha = if (hasCode) 1f else 0.5f
    }

    private fun shareReferralCode(code: String) {
        val rewardMessage = "Join using my referral code and get ${TeamViewModel.REFERRAL_BONUS_POINTS} bonus points! I'll also earn ${TeamViewModel.REFERRAL_REWARD_POINTS} points when you join."
        val shareText = getString(R.string.referral_share_text, code) + "\n\n$rewardMessage"

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        try {
            startActivity(Intent.createChooser(
                shareIntent,
                getString(R.string.share_referral_code)
            ))
        } catch (e: ActivityNotFoundException) {
            showSnackbar(getString(R.string.no_share_app), isError = true)
        }
    }

    private fun updateTeamStatsDisplay(stats: TeamViewModel.TeamStats) {
        // Update stats display if you have these views in your layout
        // Example implementation:

        // binding.tvTotalMembers?.text = "Team Members: ${stats.totalMembers}"
        // binding.tvTotalRewards?.text = "Total Earned: ${stats.totalEarnings} points"
        // binding.tvRewardPerMember?.text = "Reward per referral: ${TeamViewModel.REFERRAL_REWARD_POINTS} points"

        // If you have a stats card or section, you can show/hide it based on team size
        // binding.layoutTeamStats?.visibility = if (stats.totalMembers > 0) View.VISIBLE else View.GONE

        // Create a comprehensive stats message
        val statsMessage = buildString {
            append("Team Stats:\n")
            append("• Members: ${stats.totalMembers}\n")
            append("• Total Earned: ${stats.totalEarnings} points\n")
            append("• Reward per referral: ${TeamViewModel.REFERRAL_REWARD_POINTS} points")
            if (TeamViewModel.REFERRAL_BONUS_POINTS > 0) {
                append("\n• New member bonus: ${TeamViewModel.REFERRAL_BONUS_POINTS} points")
            }
        }

        // Log the comprehensive stats

        // Optional: Show toast with stats when team is refreshed
        if (stats.totalMembers > 0) {
            // Uncomment if you want to show stats as toast
            // showToast("You've earned ${stats.totalEarnings} points from ${stats.totalMembers} referrals!")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Referral Code", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).apply {
            if (isError) {
                setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.red))
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            } else {
                // Success color for positive messages
                setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.blue_btn))
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }
            show()
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {

        if (isEmpty) {
            // Show empty state with referral reward info
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvTeamMembers.visibility = View.GONE
            binding.searchBar.visibility = if (allTeamMembers.isEmpty()) View.GONE else View.VISIBLE

            // Optional: Update empty state message to include reward info
            // binding.tvEmptyStateMessage?.text = "Start building your team!\nEarn ${TeamViewModel.REFERRAL_REWARD_POINTS} points for each successful referral."
        } else {
            // Show team list
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvTeamMembers.visibility = View.VISIBLE
            binding.searchBar.visibility = View.VISIBLE
        }
    }

    private fun loadData() {
        viewModel.loadTeamData()
        viewModel.debugCheckTeamMembers()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when fragment becomes visible
        viewModel.refreshTeamData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}