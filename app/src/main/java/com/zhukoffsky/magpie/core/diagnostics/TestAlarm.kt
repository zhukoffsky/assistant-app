package com.zhukoffsky.magpie.core.diagnostics

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.notification.MagpieNotifications
import java.time.Duration
import java.time.Instant

/**
 * Тестовое уведомление через минуту.
 *
 * Проходит ровно тот же путь, что и настоящее напоминание: точный будильник,
 * пробуждение из Doze, показ уведомления. Поэтому если тест дошёл — дойдёт и
 * напоминание; если нет — проблема видна сразу, а не через сутки.
 */
class TestAlarmScheduler(private val context: Context) {

    fun scheduleInAMinute(): Instant {
        val at = Instant.now().plus(Duration.ofMinutes(1))
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TestAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent)
        }

        return at
    }

    private companion object {
        const val REQUEST_CODE = 2_000
    }
}

class TestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notification = NotificationCompat.Builder(context, MagpieNotifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.diag_test_notification_title))
            .setContentText(context.getString(R.string.diag_test_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Разрешение проверять не нужно: без него notify просто ничего не
        // сделает, а это и есть ответ теста.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val NOTIFICATION_ID = 2
    }
}
