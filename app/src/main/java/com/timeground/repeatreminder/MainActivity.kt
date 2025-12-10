package com.timeground.repeatreminder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadThemePreference()
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_alarm -> {
                    loadFragment(AlarmFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
        
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.timeground.repeatreminder.notification_click") {
             // Ensure HomeFragment is shown if notification clicked? 
             // Logic suggests if ringing, HomeFragment shows Stop UI (handled by its checkAlarmState)
             // If we just switch to Home, the fragment will check state.
             if (bottomNavigationView.selectedItemId != R.id.nav_home) {
                 bottomNavigationView.selectedItemId = R.id.nav_home
             }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        // Tag fragments to find them later if needed; replace for now.
        // Optimization: hide/show instead of replace to keep state (timer countdown visual),
        // but HomeFragment restores state from prefs in onResume/onReceive, so replace is fine for now 
        // to simplify. But hide/show is better for maintaining scroll position and immediate state.
        // Let's stick to replace for simplicity and robustness first, as HomeFragment logic is state-driven (prefs).
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    private fun loadThemePreference() {
        val prefs = getSharedPreferences("repeat_reminder_prefs", android.content.Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode_enabled", false)
        val mode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
