package com.example.tradeveil

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.tradeveil.databinding.ActivityMainBinding
import com.example.tradeveil.fragments.ChatFragment
import com.example.tradeveil.fragments.HomeFragment
import com.example.tradeveil.fragments.LeaderboardFragment
import com.example.tradeveil.fragments.SwapFragment
import com.example.tradeveil.fragments.TeamFragment
import com.example.tradeveil.services.ReferralCheckService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var dotViews: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        startService(Intent(this, ReferralCheckService::class.java))
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // List of all dot views under nav items
        dotViews = listOf(
            findViewById(R.id.dotHome),
            findViewById(R.id.dotLeaderboard),
            findViewById(R.id.dotChat),
            findViewById(R.id.dotTeam),
            findViewById(R.id.dotSwap)
        )

        setupNavigation()

        // Default selection: Home
        onNavItemSelected(0, HomeFragment())
    }

    private fun setupNavigation() {
        binding.navHome.setOnClickListener {
            onNavItemSelected(0, HomeFragment())
        }

        binding.navLeaderboard.setOnClickListener {
            onNavItemSelected(1, LeaderboardFragment())
        }

        binding.navChat.setOnClickListener {
            onNavItemSelected(2, ChatFragment())
        }

        binding.navTeam.setOnClickListener {
            onNavItemSelected(3, TeamFragment())
        }

        binding.navSwap.setOnClickListener {
            onNavItemSelected(4, SwapFragment())
        }
    }

    private fun onNavItemSelected(index: Int, fragment: Fragment) {
        replaceFragment(fragment)
        updateActiveDot(index)
        updateColors(index)
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun updateActiveDot(activeIndex: Int) {
        dotViews.forEachIndexed { index, dotView ->
            dotView.visibility = if (index == activeIndex) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun updateColors(activeIndex: Int) {
        val iconList = listOf(
            binding.iconHome,
            binding.iconLeaderboard,
            binding.iconChat,
            binding.iconTeam,
            binding.iconSwap
        )

        iconList.forEachIndexed { index, icon ->
            icon.isSelected = (index == activeIndex)
        }
    }



}
