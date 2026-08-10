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
import com.zhukoffsky.magpie.core.util.MagpieLog
import com.zhukoffsky.magpie.feature.meds.alarm.MedActionReceiver
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import com.zhukoffsky.magpie.feature.meds.domain.MedIntake
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderActionReceiver
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder

object MagpieNotifications {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_MEDS = "meds"

    private const val DOSE_NOTIFICATION_ID = 1

    /**
     * Идентификаторы уведомлений о напоминаниях — это id из БД, и первое же
     * напоминание получило бы id 1 и затёрло уведомление о приёме таблетки.
     * Разводим диапазоны.
     */
    private const val REMINDER_ID_OFFSET = 1_000

    /** Каналы создаются при старте процесса: повторный вызов ничего не портит. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDS,
                context.getString(R.string.channel_meds),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /**
     * Уведомление о приёме лекарства.
     *
     * Идентификатор один на всё приложение: активный приём всегда один,
     * и старое уведомление должно замещаться, а не копиться.
     */
    fun showDose(context: Context, course: MedCourse, intake: MedIntake) {
        if (!canPost(context)) {
            MagpieLog.w("notify: dose suppressed, no POST_NOTIFICATIONS")
            return
        }
        MagpieLog.i("notify: dose=${intake.doseMg}mg")

        val notification = NotificationCompat.Builder(context, CHANNEL_MEDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.med_notification_title, course.name))
            .setContentText(context.getString(R.string.med_notification_dose, intake.doseMg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.med_action_taken),
                MedActionReceiver.takenIntent(context, intake.scheduledAt),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.med_action_snooze_15),
                MedActionReceiver.snoozeIntent(context, intake.scheduledAt, minutes = 15),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.med_action_snooze_60),
                MedActionReceiver.snoozeIntent(context, intake.scheduledAt, minutes = 60),
            )
            .build()

        NotificationManagerCompat.from(context).notify(DOSE_NOTIFICATION_ID, notification)
    }

    fun cancelDose(context: Context) {
        NotificationManagerCompat.from(context).cancel(DOSE_NOTIFICATION_ID)
    }

    fun showReminder(context: Context, reminder: Reminder) {
        if (!canPost(context)) {
            MagpieLog.w("notify: reminder=${reminder.id} suppressed, no POST_NOTIFICATIONS")
            return
        }
        MagpieLog.i("notify: reminder=${reminder.id}")

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

        NotificationManagerCompat.from(context).notify(notificationId(reminder.id), notification)
    }

    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun notificationId(reminderId: Long): Int = (reminderId + REMINDER_ID_OFFSET).toInt()

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
