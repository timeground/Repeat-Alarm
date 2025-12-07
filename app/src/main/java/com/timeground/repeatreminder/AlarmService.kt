package com.timeground.repeatreminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isRinging = false

    companion object {
        const val CHANNEL_ID = "AlarmServiceChannel"
        const val ACTION_STOP = "STOP_ALARM"
        const val ACTION_START = "START_ALARM"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        stopAlarm()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            if (!isRinging) {
                startForeground(1, createNotification())
                playAlarm()
                isRinging = true
                saveRingingState(true)
            }
        }

        return START_STICKY
    }

    private fun playAlarm() {
        try {
            // Play Sound using MediaPlayer for better control (Looping vs One-shot)
            val soundUri = getSavedRingtoneUri()
            if (soundUri != null) {
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(applicationContext, soundUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare() // Valid since we are on main thread (Service) and local file
                    
                    // SMART DURATION LOGIC:
                    // If sound is short (< 4 seconds), treat as Notification (Play once, hard stop at 2s).
                    // If sound is long (>= 4 seconds), treat as Alarm (Loop, manual stop).
                    val durationMs = duration
                    if (durationMs < 4000) {
                        isLooping = false
                        // Auto-stop after 2 seconds (User requested)
                        handler.postDelayed(timeoutRunnable, 2000)
                    } else {
                        isLooping = true
                        // NO TIMEOUT - Rings until user stops it
                    }
                    start()
                }
            }

            // Vibrate
            if (getVibrationPreference()) {
                if (vibrator?.hasVibrator() == true) {
                    // Signal is Alarm? Pattern should be more "alarming" if long?
                    // For consistency, keep same pattern but loop if it's an alarm?
                    // Let's stick to the current pattern, but loop it if it's an alarm.
                    
                    val pattern = longArrayOf(0, 500, 200, 500) 
                    // Determine repeat based on sound heuristic for consistency?
                    // Actually, let's keep vibration simple: loop it (-1 is no loop, 0 is loop)
                    // If we want "Real Alarm", we should loop vibration too.
                    // Let's default to NO LOOP for vibration to avoid annoyance on short beeps,
                    // unless we want to be smart here too. 
                    // Smart Vib: If looping sound, loop vibe.
                    val repeatIndex = if (mediaPlayer?.isLooping == true) 0 else -1
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, repeatIndex))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, repeatIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Error playing alarm", e)
            // Fallback: Ensure to stop if error
            stopSelf()
        }
    }

    private fun stopAlarm() {
        try {
            handler.removeCallbacks(timeoutRunnable)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            vibrator?.cancel()
            
            isRinging = false
            saveRingingState(false)
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping alarm", e)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Repeat Alarm")
            .setContentText("Alarm is ringing")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority so it shows up
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            
        // If it's a looping alarm, make it ongoing
        if (mediaPlayer?.isLooping == true) {
             builder.setOngoing(true)
        }
            
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getSavedRingtoneUri(): Uri? {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("sound_uri", "")
        return if (uriString.isNullOrEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(uriString)
    }

    private fun getVibrationPreference(): Boolean {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("vibration_enabled", true)
    }
    
    private fun saveRingingState(isRinging: Boolean) {
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_alarm_ringing", isRinging).apply()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
