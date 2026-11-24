package com.ewbible.app.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Calendar

object ReminderManager {
    private const val PREFS = "ew_reminder"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_ENABLED = "enabled"

    fun getReminder(context: Context): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return null
        val h = prefs.getInt(KEY_HOUR, -1)
        val m = prefs.getInt(KEY_MINUTE, -1)
        if (h < 0 || m < 0) return null
        return h to m
    }

    fun getReminderText(context: Context): String {
        val r = getReminder(context) ?: return "Set Reminder"
        val hour12 = when {
            r.first == 0 -> 12
            r.first > 12 -> r.first - 12
            else -> r.first
        }
        val amPm = if (r.first < 12) "AM" else "PM"
        return String.format("%02d:%02d %s", hour12, r.second, amPm)
    }

    // Call this BEFORE setReminder() - opens system screen to allow exact alarms
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarm.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try { context.startActivity(intent) } catch (e: Exception) { }
            }
        }
    }

    fun setReminder(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putBoolean(KEY_ENABLED, true)
            .apply()
        try {
            schedule(context, hour, minute)
        } catch (e: SecurityException) {
            // Exact alarm denied - fallback to inexact, still fires
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            try {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent(context))
            } catch (e2: Exception) { }
        } catch (e: Exception) { }
    }

    fun cancelReminder(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { alarmManager.cancel(pendingIntent(context)) } catch (e: Exception) { }
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent(context)
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent(context)
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent(context)
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent(context)
            )
        }
    }

    fun rescheduleIfEnabled(context: Context) {
        val r = getReminder(context) ?: return
        try { schedule(context, r.first, r.second) } catch (e: Exception) { }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BibleReminderReceiver::class.java).apply {
            action = "com.ewbible.app.ACTION_BIBLE_REMINDER"
        }
        return PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}