package com.timeground.repeatreminder

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var switchDarkMode: SwitchCompat
    private lateinit var switchRingIndefinitely: SwitchCompat
    private lateinit var switchPopup: SwitchCompat
    private lateinit var layoutDuration: LinearLayout
    private lateinit var seekBarDuration: SeekBar
    private lateinit var tvDurationLabel: TextView
    
    private var ringingDuration = -1 
    private var isPopupEnabled = true 
    private var isDarkMode = false 

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // No need for setSupportActionBar here since we are in a fragment.
        // The toolbar in xml acts as a header.

        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        switchRingIndefinitely = view.findViewById(R.id.switchRingIndefinitely)
        switchPopup = view.findViewById(R.id.switchPopup)
        layoutDuration = view.findViewById(R.id.layoutDuration)
        seekBarDuration = view.findViewById(R.id.seekBarDuration)
        tvDurationLabel = view.findViewById(R.id.tvDurationLabel)

        loadPreferences()
        setupListeners()
        updateUI()
    }
    
    private fun loadPreferences() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        ringingDuration = prefs.getInt("ringing_duration", -1)
        isPopupEnabled = prefs.getBoolean("popup_enabled", true)
        isDarkMode = prefs.getBoolean("dark_mode_enabled", false)
    }

    private fun savePreferences() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("ringing_duration", ringingDuration).apply()
        prefs.edit().putBoolean("popup_enabled", isPopupEnabled).apply()
        prefs.edit().putBoolean("dark_mode_enabled", isDarkMode).apply()
    }

    private fun setupListeners() {
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            isDarkMode = isChecked
            savePreferences()
            applyTheme(isDarkMode)
        }

        switchRingIndefinitely.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                ringingDuration = -1
            } else {
                if (ringingDuration == -1) {
                    ringingDuration = 30
                }
            }
            savePreferences()
            updateUI()
        }

        seekBarDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    ringingDuration = progress + 1
                    updateDurationLabel()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savePreferences()
            }
        })
        
        switchPopup.setOnCheckedChangeListener { _, isChecked ->
            isPopupEnabled = isChecked
            savePreferences()
        }
    }

    private fun updateUI() {
        switchDarkMode.isChecked = isDarkMode
        switchPopup.isChecked = isPopupEnabled
        
        if (ringingDuration == -1) {
            switchRingIndefinitely.isChecked = true
            layoutDuration.visibility = View.GONE
        } else {
            switchRingIndefinitely.isChecked = false
            layoutDuration.visibility = View.VISIBLE
            
            if (ringingDuration < 1) ringingDuration = 1
            if (ringingDuration > 60) ringingDuration = 60
            
            seekBarDuration.progress = ringingDuration - 1
            updateDurationLabel()
        }
    }

    private fun updateDurationLabel() {
        tvDurationLabel.text = "Ringing Duration: $ringingDuration seconds"
    }

    private fun applyTheme(isDark: Boolean) {
        val mode = if (isDark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
