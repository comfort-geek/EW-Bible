package com.ewbible.app.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ewbible.app.MainActivity
import com.ewbible.app.R

class BibleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // After reboot, re-schedule
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val r = ReminderManager.getReminder(context)
            if (r != null) ReminderManager.schedule(context, r.first, r.second)
            return
        }

        // Show notification with sound
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "bible_reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bible Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily Bible reading reminder"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("📖 Time to read the Bible")
            .setContentText("Your daily Bible reading time is now. Open EW Bible.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pending)
            .build()

        manager.notify(1001, notification)

        // Re-schedule for tomorrow — so it repeats every day automatically
        val r = ReminderManager.getReminder(context)
        if (r != null) ReminderManager.schedule(context, r.first, r.second)
    }
}
