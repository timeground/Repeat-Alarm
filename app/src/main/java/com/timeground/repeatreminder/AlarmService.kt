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
        const val CHANNEL_HIGH_ID = "AlarmServiceChannelHigh_v2"
        const val CHANNEL_LOW_ID = "AlarmServiceChannelLow"
        // Legecy ID for compatibility or general use
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
        stopAlarm(fromTimeout = true)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopAlarm(fromTimeout = false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            if (!isRinging) {
                startForeground(1, createNotification())
                playAlarm()
                isRinging = true
                saveRingingState(true)
                
                // Notify UI immediately that ringing started
                val updateIntent = Intent("com.timeground.repeatreminder.UPDATE_UI")
                updateIntent.setPackage(packageName)
                updateIntent.putExtra("is_ringing", true)
                sendBroadcast(updateIntent)
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
                    
                    // Logic based on User Preference
                    val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
                    val ringingDuration = prefs.getInt("ringing_duration", -1) // -1 is Indefinite

                    isLooping = true // Always loop to ensure we fill the duration (or indefinite)

                    if (ringingDuration != -1) {
                        // User set a specific duration (in seconds)
                        handler.postDelayed(timeoutRunnable, ringingDuration * 1000L)
                    } else {
                        // Indefinite - No timeout
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

    private fun stopAlarm(fromTimeout: Boolean = false) {
        try {
            handler.removeCallbacks(timeoutRunnable)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            vibrator?.cancel()
            
            isRinging = false
            saveRingingState(false)
            
            if (fromTimeout) {
                // If timed out, update notification to show it's finished but keep it in tray
                updateNotificationFinished()
                // Detach from foreground but keep notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    stopForeground(false)
                }
            } else {
                 // User dismissed it, remove notification
                 stopForeground(true)
            }
            
            // Notify UI to dismiss the "Dismiss" button and revert to normal state
            val updateIntent = Intent("com.timeground.repeatreminder.UPDATE_UI")
            updateIntent.setPackage(packageName)
            sendBroadcast(updateIntent)
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping alarm", e)
        }
    }
    
    private fun updateNotificationFinished() {
        // Increment and get alarm count
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        var count = prefs.getInt("alarm_count", 0)
        count++
        prefs.edit().putInt("alarm_count", count).apply()
        
        val countText = "Alarm rang $count time" + if(count > 1) "s" else ""
        
        val manager = getSystemService(NotificationManager::class.java)
        
        // Use Low channel for finished status
        val builder = NotificationCompat.Builder(this, CHANNEL_LOW_ID)
            .setContentTitle("Repeat Alarm")
            .setContentText(countText)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ))
            .setAutoCancel(true) // Dismiss when clicked
            
        manager.notify(1, builder.build())
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val prefs = getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val startPopup = prefs.getBoolean("popup_enabled", true)
        
        val channelId = if (startPopup) CHANNEL_HIGH_ID else CHANNEL_LOW_ID
        val priority = if (startPopup) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Repeat Alarm")
            .setContentText("Alarm is ringing")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        if (startPopup) {
            val fullScreenIntent = Intent(this, AlarmActivity::class.java)
            // Required for starting activity from outside of an activity context
            fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) 
            
            val fullScreenPendingIntent = PendingIntent.getActivity(
                this, 0, fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            
            // Explicitly try to start activity (works if app is in foreground)
            try {
                startActivity(fullScreenIntent)
            } catch (e: Exception) {
                Log.e("AlarmService", "Failed to start activity directly", e)
            }
        }
            
        // If it's a looping alarm, make it ongoing
        if (mediaPlayer?.isLooping == true) {
             builder.setOngoing(true)
        }
            
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // High Importance Channel (Popup)
            val channelHigh = NotificationChannel(
                CHANNEL_HIGH_ID,
                "Alarm Service High (Popup)",
                NotificationManager.IMPORTANCE_HIGH
            )
            // No sound from notification itself, handled by MediaPlayer
            channelHigh.setSound(null, null) 
            manager.createNotificationChannel(channelHigh)
            
            // Low Importance Channel (No Popup)
            val channelLow = NotificationChannel(
                CHANNEL_LOW_ID,
                "Alarm Service Low (No Popup)",
                NotificationManager.IMPORTANCE_LOW
            )
            channelLow.setSound(null, null)
            manager.createNotificationChannel(channelLow)
            
            // Legacy channel cleanup or keep for fallback
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
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
        // If destroyed by system or stopSelf, ensure resources are released
        // Default to not removing notification if we assume it might be timeout path,
        // but typically stopAlarm is called before.
        // If we just call stopAlarm() here, user might have just swiped away task.
        stopAlarm(fromTimeout = false) 
        super.onDestroy()
    }
}
