package com.timeground.repeatreminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.app.Activity.RESULT_OK
import androidx.appcompat.widget.AppCompatButton

class HomeFragment : Fragment() {

    private lateinit var tvInterval: TextView
    private lateinit var btnAction: Button
    private lateinit var tvCountdown: TextView
    private lateinit var tvNextAlarm: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvStartLabel: TextView
    private lateinit var btnResetTime: TextView
    private lateinit var switchVibration: SwitchCompat
    
    private var isRunning = false
    private var intervalMinutes = 15
    private var countDownTimer: CountDownTimer? = null
    private var nextAlarmTime: Long = 0
    private var startTimeCalendar: Calendar? = null
    private var use24HourFormat = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            val delay = 1000 - (now % 1000)
            handler.postDelayed(this, delay)
        }
    }
    
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.timeground.repeatreminder.UPDATE_UI") {
                if (intent.getBooleanExtra("is_ringing", false)) {
                     showStopAlarmUI()
                     return
                }

                val nextTime = intent.getLongExtra("next_alarm_time", 0)
                if (nextTime > 0) {
                    nextAlarmTime = nextTime
                    val prefs = requireContext().getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("next_alarm_time", nextTime).apply()
                    
                    val isRinging = prefs.getBoolean("is_alarm_ringing", false)
                    if (isRinging) {
                        showStopAlarmUI()
                    } else if (isRunning) {
                        startCountdown()
                    } else {
                        restoreState()
                    }
                } else {
                    restoreState()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvInterval = view.findViewById(R.id.tvInterval)
        btnAction = view.findViewById(R.id.btnAction)
        tvCountdown = view.findViewById(R.id.tvCountdown)
        tvNextAlarm = view.findViewById(R.id.tvNextAlarm)
        tvClock = view.findViewById(R.id.tvClock)
        tvStartLabel = view.findViewById(R.id.tvStartLabel)
        btnResetTime = view.findViewById(R.id.btnResetTime)
        switchVibration = view.findViewById(R.id.switchVibration)
        
        tvClock.setOnClickListener { showTimePickerDialog() }
        btnResetTime.setOnClickListener { resetToNow() }
        
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            saveVibrationPreference(isChecked)
        }
        
        tvInterval.setOnClickListener { showDurationPicker() }
        
        handler.post(clockRunnable)
        
        loadVibrationPreference()
        
        val btnMinus = view.findViewById<Button>(R.id.btnMinus)
        val btnPlus = view.findViewById<Button>(R.id.btnPlus)
        
        val btn5m = view.findViewById<Button>(R.id.btn5m)
        val btn10m = view.findViewById<Button>(R.id.btn10m)
        val btn15m = view.findViewById<Button>(R.id.btn15m)
        val btn30m = view.findViewById<Button>(R.id.btn30m)
        val btn60m = view.findViewById<Button>(R.id.btn60m)
        
        val presets = listOf(btn5m, btn10m, btn15m, btn30m, btn60m)

        btnMinus.setOnClickListener { adjustInterval(-1, presets) }
        btnPlus.setOnClickListener { adjustInterval(1, presets) }
        
        btn5m.setOnClickListener { setPreset(5, presets) }
        btn10m.setOnClickListener { setPreset(10, presets) }
        btn15m.setOnClickListener { setPreset(15, presets) }
        btn30m.setOnClickListener { setPreset(30, presets) }
        btn60m.setOnClickListener { setPreset(60, presets) }
        
        btnAction.setOnClickListener { toggleReminder() }

        updatePresetUI(intervalMinutes, presets)
        checkPermissions()
    }

    private fun setPreset(minutes: Int, buttons: List<Button>) {
        if (isRunning) return
        intervalMinutes = minutes
        updateIntervalDisplay()
        updatePresetUI(minutes, buttons)
    }

    private fun updateIntervalDisplay() {
        val hours = intervalMinutes / 60
        val mins = intervalMinutes % 60
        val text = if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
        tvInterval.text = text
    }

    private fun updatePresetUI(selectedMinutes: Int, buttons: List<Button>) {
        val context = context ?: return
        buttons.forEach { btn ->
            val btnMinutes = btn.text.toString().replace("m", "").toIntOrNull() ?: 0
            if (btnMinutes == selectedMinutes) {
                btn.setTextColor(ContextCompat.getColor(context, R.color.white))
                btn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.blue_preset)
            } else {
                btn.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                btn.backgroundTintList = ContextCompat.getColorStateList(context, R.color.gray_preset)
            }
        }
    }

    private fun adjustInterval(delta: Int, buttons: List<Button>) {
        if (isRunning) return
        var current = intervalMinutes
        current += delta
        if (current < 1) current = 1
        intervalMinutes = current
        updateIntervalDisplay()
        updatePresetUI(current, buttons)
    }

    private fun toggleReminder() {
        if (isRunning) {
            stopReminder()
        } else {
            startReminder()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh preferences
        loadTimeFormatPreference()
        updateClock()
        if (startTimeCalendar != null) {
            val hour = startTimeCalendar!!.get(Calendar.HOUR_OF_DAY)
            val minute = startTimeCalendar!!.get(Calendar.MINUTE)
            updateStartLabel(hour, minute)
        }

        checkAlarmState()
        
        val filter = IntentFilter("com.timeground.repeatreminder.UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(updateReceiver, filter)
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun checkAlarmState() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val isRinging = prefs.getBoolean("is_alarm_ringing", false)
        
        if (isRinging) {
            showStopAlarmUI()
        } else {
            restoreState()
        }
    }

    private fun showStopAlarmUI() {
        if (!isAdded) return
        btnAction.text = getString(R.string.dismiss)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red_stop)
        btnAction.setOnClickListener { stopAlarmService() }
        
        tvCountdown.visibility = TextView.VISIBLE
        tvCountdown.text = "ALARM RINGING"
        tvNextAlarm.visibility = TextView.INVISIBLE
        
        tvInterval.isEnabled = false
        tvClock.isEnabled = false
        btnResetTime.isEnabled = false
    }

    private fun stopAlarmService() {
        val context = context ?: return
        val intent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        context.startService(intent)
        
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_alarm_ringing", false).apply()
        
        restoreState()
    }

    private fun saveState(running: Boolean, interval: Int, nextAlarm: Long) {
        val context = context ?: return
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_running", running)
            putInt("interval_minutes", interval)
            putLong("next_alarm_time", nextAlarm)
            apply()
        }
    }

    private fun restoreState() {
        val context = context ?: return
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        isRunning = prefs.getBoolean("is_running", false)
        intervalMinutes = prefs.getInt("interval_minutes", 15)
        nextAlarmTime = prefs.getLong("next_alarm_time", 0)
        
        val receiverNextTime = prefs.getLong("next_alarm_time", 0)
        if (receiverNextTime > nextAlarmTime) {
            nextAlarmTime = receiverNextTime
        }

        if (isRunning) {
            if (nextAlarmTime < System.currentTimeMillis()) {
                val now = System.currentTimeMillis()
                nextAlarmTime = now + (intervalMinutes * 60 * 1000)
                
                saveState(true, intervalMinutes, nextAlarmTime)
                
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = "com.timeground.repeatreminder.ALARM_TRIGGER"
                    putExtra("interval", intervalMinutes)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                }
            }

            updateIntervalDisplay()
            btnAction.text = getString(R.string.stop_reminder)
            btnAction.backgroundTintList = ContextCompat.getColorStateList(context, R.color.red_stop)
            btnAction.setOnClickListener { toggleReminder() }
            
            tvInterval.isEnabled = false
            tvClock.isEnabled = false
            btnResetTime.isEnabled = false
            
            startCountdown()
        } else {
            stopReminderUI()
        }
    }

    private fun startReminder() {
        if (!checkPermissions()) return

        if (intervalMinutes <= 0) {
            Toast.makeText(context, "Invalid interval", Toast.LENGTH_SHORT).show()
            return
        }

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val triggerTime = if (startTimeCalendar != null) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startTimeCalendar!!.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, startTimeCalendar!!.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            target.timeInMillis
        } else {
            System.currentTimeMillis() + (intervalMinutes * 60 * 1000)
        }
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.timeground.repeatreminder.ALARM_TRIGGER"
            putExtra("interval", intervalMinutes)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        isRunning = true
        nextAlarmTime = triggerTime
        
        saveState(true, intervalMinutes, nextAlarmTime)
        
        val prefs = requireContext().getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("alarm_count", 0).apply()
        
        btnAction.text = getString(R.string.stop_reminder)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red_stop)
        tvInterval.isEnabled = false
        tvClock.isEnabled = false
        btnResetTime.isEnabled = false
        
        startCountdown()
    }

    private fun stopReminder() {
        val context = context ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.timeground.repeatreminder.ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        stopAlarmService()

        isRunning = false
        saveState(false, intervalMinutes, 0)
        
        stopReminderUI()
    }
    
    private fun stopReminderUI() {
        if (!isAdded) return
        countDownTimer?.cancel()
        tvCountdown.visibility = TextView.INVISIBLE
        tvNextAlarm.visibility = TextView.INVISIBLE
        
        btnAction.text = getString(R.string.start_reminder)
        btnAction.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green_start)
        btnAction.setOnClickListener { toggleReminder() }
        
        tvInterval.isEnabled = true
        tvClock.isEnabled = true
        btnResetTime.isEnabled = true
    }

    private fun startCountdown() {
        tvCountdown.visibility = TextView.VISIBLE
        tvNextAlarm.visibility = TextView.VISIBLE
        
        countDownTimer?.cancel()
        
        val duration = nextAlarmTime - System.currentTimeMillis()
        
        if (duration < 0) {
             tvCountdown.text = "00:00"
             return
        }
        
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isAdded) return
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvCountdown.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
            }
        }.start()
        
        val pattern = if (use24HourFormat) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        tvNextAlarm.text = "Next Alarm: ${sdf.format(java.util.Date(nextAlarmTime))}"
    }

    private fun checkPermissions(): Boolean {
        val context = context ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return false
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                return false
            }
        }
        return true
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            startTimeCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
            }
            
            updateStartLabel(selectedHour, selectedMinute)
            btnResetTime.visibility = View.VISIBLE
            
        }, hour, minute, use24HourFormat).show()
    }
    
    private fun showDurationPicker() {
        if (isRunning) return
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_duration_picker, null)
        val npHours = dialogView.findViewById<android.widget.NumberPicker>(R.id.npHours)
        val npMinutes = dialogView.findViewById<android.widget.NumberPicker>(R.id.npMinutes)
        
        npHours.minValue = 0
        npHours.maxValue = 23
        npMinutes.minValue = 0
        npMinutes.maxValue = 59
        
        val currentHours = intervalMinutes / 60
        val currentMinutes = intervalMinutes % 60
        
        npHours.value = currentHours
        npMinutes.value = currentMinutes
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Set Interval Duration")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val h = npHours.value
                val m = npMinutes.value
                val total = (h * 60) + m
                if (total > 0) {
                    intervalMinutes = total
                    updateIntervalDisplay()
                    
                    // Reset preset buttons since custom time might not match any
                    val btn5m = view?.findViewById<Button>(R.id.btn5m)
                    val btn10m = view?.findViewById<Button>(R.id.btn10m)
                    val btn15m = view?.findViewById<Button>(R.id.btn15m)
                    val btn30m = view?.findViewById<Button>(R.id.btn30m)
                    val btn60m = view?.findViewById<Button>(R.id.btn60m)
                    val presets = listOfNotNull(btn5m, btn10m, btn15m, btn30m, btn60m)
                    updatePresetUI(intervalMinutes, presets)

                } else {
                    Toast.makeText(requireContext(), "Interval must be > 0", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateStartLabel(hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val pattern = if (use24HourFormat) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        val timeText = "STARTING AT ${sdf.format(calendar.time)}"
        tvStartLabel.text = timeText
    }
    
    private fun resetToNow() {
        startTimeCalendar = null
        tvStartLabel.text = "SELECT START TIME"
        btnResetTime.visibility = View.INVISIBLE
    }
    
    private fun updateClock() {
        if (!isAdded) return
        val pattern = if (use24HourFormat) "HH:mm:ss" else "hh:mm:ss a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        tvClock.text = sdf.format(java.util.Date())
    }
    

    

    
    private fun saveVibrationPreference(enabled: Boolean) {
        val prefs = requireContext().getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }
    
    private fun loadVibrationPreference() {
        val prefs = requireContext().getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("vibration_enabled", true)
        switchVibration.isChecked = enabled
    }

    private fun loadTimeFormatPreference() {
        val prefs = requireContext().getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        use24HourFormat = prefs.getBoolean("use_24_hour_format", false)
    }
    

    
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(clockRunnable)
    }
}
