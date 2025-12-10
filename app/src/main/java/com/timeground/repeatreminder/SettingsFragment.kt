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
    private lateinit var switchTimeFormat: SwitchCompat
    private lateinit var tvSound: TextView
    private lateinit var layoutDuration: LinearLayout
    private lateinit var seekBarDuration: SeekBar
    private lateinit var tvDurationLabel: TextView
    private lateinit var tvRingIndefinitelyDesc: TextView
    
    private var ringingDuration = -1 
    private var isPopupEnabled = true 
    private var isDarkMode = false 
    private var use24HourFormat = false
    private var currentRingtoneUri: android.net.Uri? = null

    private val ringtoneLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val uri: android.net.Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            saveRingtone(uri)
        }
    }

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
        switchTimeFormat = view.findViewById(R.id.switchTimeFormat)
        tvSound = view.findViewById(R.id.tvSound)
        layoutDuration = view.findViewById(R.id.layoutDuration)
        seekBarDuration = view.findViewById(R.id.seekBarDuration)
        tvDurationLabel = view.findViewById(R.id.tvDurationLabel)
        tvRingIndefinitelyDesc = view.findViewById(R.id.tvRingIndefinitelyDesc)

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
        use24HourFormat = prefs.getBoolean("use_24_hour_format", false)
        
        val uriString = prefs.getString("alarm_sound_uri", null)
        currentRingtoneUri = uriString?.let { android.net.Uri.parse(it) } ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
    }

    private fun savePreferences() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("ringing_duration", ringingDuration).apply()
        prefs.edit().putBoolean("popup_enabled", isPopupEnabled).apply()
        prefs.edit().putBoolean("dark_mode_enabled", isDarkMode).apply()
        
        // 24hr format is saved immediately in listener
    }

    private fun setupListeners() {
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            isDarkMode = isChecked
            savePreferences()
            applyTheme(isDarkMode)
        }
        
        switchPopup.setOnCheckedChangeListener { _, isChecked ->
            isPopupEnabled = isChecked
            savePreferences()
        }
        
        switchTimeFormat.setOnCheckedChangeListener { _, isChecked ->
             use24HourFormat = isChecked
             val context = context ?: return@setOnCheckedChangeListener
             val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
             prefs.edit().putBoolean("use_24_hour_format", isChecked).apply()
        }
        
        tvSound.setOnClickListener { pickRingtone() }

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
    }
    
    private fun pickRingtone() {
        val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
        intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtoneUri)
        ringtoneLauncher.launch(intent)
    }
    
    private fun saveRingtone(uri: android.net.Uri?) {
        currentRingtoneUri = uri
        val context = context ?: return
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        if (uri != null) {
            prefs.edit().putString("alarm_sound_uri", uri.toString()).apply()
        } else {
            prefs.edit().putString("alarm_sound_uri", null).apply()
        }
        updateSoundUI()
    }

    private fun updateUI() {
        switchDarkMode.isChecked = isDarkMode
        switchRingIndefinitely.isChecked = (ringingDuration == -1)
        switchPopup.isChecked = isPopupEnabled
        switchTimeFormat.isChecked = use24HourFormat

        if (ringingDuration == -1) {
             layoutDuration.visibility = View.GONE
             tvRingIndefinitelyDesc.visibility = View.VISIBLE
        } else {
             layoutDuration.visibility = View.VISIBLE
             tvRingIndefinitelyDesc.visibility = View.GONE
             seekBarDuration.progress = ringingDuration - 1
             updateDurationLabel()
        }
        updateSoundUI()
    }
    
    private fun updateSoundUI() {
        val ringtoneTitle = if (currentRingtoneUri != null) {
            android.media.RingtoneManager.getRingtone(context, currentRingtoneUri)?.getTitle(context) ?: "Unknown"
        } else {
            "Silent"
        }
        tvSound.text = "Sound: $ringtoneTitle"
    }

    private fun updateDurationLabel() {
        tvDurationLabel.text = "Ringing Duration: ${ringingDuration}s"
    }

    private fun applyTheme(isDark: Boolean) {
        if (isDark) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}    

