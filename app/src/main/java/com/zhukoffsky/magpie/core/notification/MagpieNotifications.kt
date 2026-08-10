package com.zhukoffsky.magpie.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zhukoffsky.magpie.MainActivity
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderActionReceiver
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder

object MagpieNotifications {

    const val CHANNEL_REMINDERS = "reminders"

    /** Каналы создаются при старте процесса: повторный вызов ничего не портит. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun showReminder(context: Context, reminder: Reminder) {
        if (!canPost(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.reminder_action_done),
                ReminderActionReceiver.donePendingIntent(context, reminder.id),
            )
            .build()

        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
    }

    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
    }

    /**
     * С Android 13 уведомления требуют разрешения. Без проверки
     * `notify` тихо ничего не делает, а мы бы считали, что напомнили.
     */
    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
