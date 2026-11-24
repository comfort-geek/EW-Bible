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
import androidx.core.app.NotificationManagerCompat
import com.ewbible.app.MainActivity
import com.ewbible.app.R

class BibleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val saved = ReminderManager.getReminder(context)

        // 1. ALWAYS reschedule first, even for BOOT
        if (saved != null) {
            try {
                ReminderManager.schedule(context, saved.first, saved.second)
            } catch (e: Exception) { }
        }

        // 2. Boot = only reschedule, don't show notification now
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) return
        if (saved == null) return

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
                setShowBadge(true)
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
            .setSmallIcon(R.mipmap.ic_launcher) // ✅ FIX: was android.R.drawable.ic_dialog_info
            .setContentTitle("📖 Time to read the Bible")
            .setContentText("Your daily Bible reading time is now. Open EW Bible.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true) // ✅ FIX: forces it to show even if phone is locked / Doze
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied - user turned it off in settings
        } catch (e: Exception) { }
    }
}