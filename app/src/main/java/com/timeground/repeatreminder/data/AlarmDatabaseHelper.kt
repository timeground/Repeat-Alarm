package com.timeground.repeatreminder.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AlarmDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "alarms.db"
        const val DATABASE_VERSION = 1
        const val TABLE_ALARMS = "alarms"
        const val COLUMN_ID = "id"
        const val COLUMN_HOUR = "hour"
        const val COLUMN_MINUTE = "minute"
        const val COLUMN_ENABLED = "enabled"
        const val COLUMN_DAYS = "days"
        const val COLUMN_LABEL = "label"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE $TABLE_ALARMS (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_HOUR INTEGER, " +
                "$COLUMN_MINUTE INTEGER, " +
                "$COLUMN_ENABLED INTEGER, " +
                "$COLUMN_DAYS TEXT, " +
                "$COLUMN_LABEL TEXT)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ALARMS")
        onCreate(db)
    }

    fun addAlarm(alarm: Alarm): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_HOUR, alarm.hour)
            put(COLUMN_MINUTE, alarm.minute)
            put(COLUMN_ENABLED, if (alarm.isEnabled) 1 else 0)
            put(COLUMN_DAYS, alarm.days)
            put(COLUMN_LABEL, alarm.label)
        }
        val id = db.insert(TABLE_ALARMS, null, values)
        db.close()
        return id
    }

    fun updateAlarm(alarm: Alarm): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_HOUR, alarm.hour)
            put(COLUMN_MINUTE, alarm.minute)
            put(COLUMN_ENABLED, if (alarm.isEnabled) 1 else 0)
            put(COLUMN_DAYS, alarm.days)
            put(COLUMN_LABEL, alarm.label)
        }
        val rows = db.update(TABLE_ALARMS, values, "$COLUMN_ID = ?", arrayOf(alarm.id.toString()))
        db.close()
        return rows
    }

    fun deleteAlarm(id: Long): Int {
        val db = writableDatabase
        val rows = db.delete(TABLE_ALARMS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return rows
    }

    fun getAllAlarms(): List<Alarm> {
        val alarmList = ArrayList<Alarm>()
        val selectQuery = "SELECT * FROM $TABLE_ALARMS"
        val db = readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val alarm = Alarm(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    hour = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HOUR)),
                    minute = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MINUTE)),
                    isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1,
                    days = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DAYS)),
                    label = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABEL))
                )
                alarmList.add(alarm)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return alarmList
    }
    
    fun getAlarm(id: Long): Alarm? {
        val db = readableDatabase
        val cursor = db.query(TABLE_ALARMS, null, "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null)
        var alarm: Alarm? = null
        if (cursor.moveToFirst()) {
             alarm = Alarm(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                hour = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_HOUR)),
                minute = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MINUTE)),
                isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1,
                days = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DAYS)),
                label = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABEL))
            )
        }
        cursor.close()
        db.close()
        return alarm
    }
}
