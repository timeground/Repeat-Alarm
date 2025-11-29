package com.example.repeatreminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == "com.example.repeatreminder.ALARM_TRIGGER") {
            showNotification(context)
            
            // Reschedule next alarm if it's a repeating one
            val intervalMinutes = intent.getIntExtra("interval", 0)
            if (intervalMinutes > 0) {
                scheduleNextAlarm(context, intervalMinutes)
            }
        } else if (action == "com.example.repeatreminder.TEST_TRIGGER") {
            showNotification(context, "Test Notification 🧪", "If you see this, notifications are working!")
        }
    }

    private fun showNotification(context: Context, title: String = "Time's up! ⏰", content: String? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Use a dynamic channel ID based on the sound URI and vibration setting
        val soundUri = getSavedRingtoneUri(context)
        val isVibrationEnabled = getVibrationPreference(context)
        val soundHash = soundUri?.toString()?.hashCode() ?: "default".hashCode()
        val channelId = "repeat_reminder_channel_${soundHash}_$isVibrationEnabled"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Repeat Reminder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(isVibrationEnabled)
                enableLights(true)
                
                // Set sound for channel (required for Android O+)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content ?: "This is your reminder.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            
        if (isVibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 500, 200, 500))
        } else {
            builder.setVibrate(null)
        }
            
        // Set sound for pre-O devices
        if (soundUri != null) {
            builder.setSound(soundUri)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun scheduleNextAlarm(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.repeatreminder.ALARM_TRIGGER"
            putExtra("interval", intervalMinutes)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle for precision even in Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
        }
    }
    private fun getSavedRingtoneUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("sound_uri", "")
        return if (uriString.isNullOrEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(uriString)
    }

    private fun getVibrationPreference(context: Context): Boolean {
        val prefs = context.getSharedPreferences("repeat_reminder_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("vibration_enabled", true)
    }
}
