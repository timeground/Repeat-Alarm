package com.timeground.repeatreminder.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.timeground.repeatreminder.AlarmReceiver
import com.timeground.repeatreminder.data.Alarm
import java.util.Calendar

object AlarmScheduler {

    fun scheduleAlarm(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancelAlarm(context, alarm)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.timeground.repeatreminder.STANDARD_ALARM_TRIGGER"
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_label", alarm.label)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(), // Unique RequestCode based on ID
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Logic to find the next valid trigger time based on selected days
        val selectedDays = alarm.days.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        // Day mapping: Calendar.SUNDAY=1 ... SATURDAY=7. 
        // Our storage: 0=Sun, 1=Mon, ... 6=Sat (consistent with typical 0-indexed arrays but need mapping)
        // Let's assume standard: Sun=0, Mon=1...Sat=6.
        // Calendar: Sun=1, Mon=2...Sat=7.
        // Mapping: CalendarDay = (OurDay + 1)
        
        var addedDays = 0
        if (selectedDays.isNotEmpty()) {
            val now = System.currentTimeMillis()
            while (addedDays < 8) { // Safety break
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1-7
                val ourDay = currentDayOfWeek - 1 // 0-6
                
                // If current calc time is in future AND it's a valid day
                if (calendar.timeInMillis > now && selectedDays.contains(ourDay)) {
                    break
                }
                
                // Move to next day
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                addedDays++
            }
            // If loop finishes without break (unlikely if set has days), it sets to next week same day roughly? 
            // Or just allow the last increment.
        } else {
             // No days selected = One off alarm
             if (calendar.timeInMillis <= System.currentTimeMillis()) {
                 calendar.add(Calendar.DAY_OF_YEAR, 1)
             }
        }
        
        val triggerTime = calendar.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
        
        Log.d("AlarmScheduler", "Scheduled alarm ${alarm.id} for $triggerTime")
    }

    fun cancelAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.timeground.repeatreminder.STANDARD_ALARM_TRIGGER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("AlarmScheduler", "Cancelled alarm ${alarm.id}")
    }
}
