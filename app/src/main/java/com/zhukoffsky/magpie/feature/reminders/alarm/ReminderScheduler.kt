package com.zhukoffsky.magpie.feature.reminders.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zhukoffsky.magpie.core.util.MagpieLog
import java.time.Instant

/**
 * Абстракция над будильниками. Нужна не ради «чистой архитектуры», а чтобы
 * логику пересчёта повторов можно было тестировать без Android.
 */
interface ReminderScheduler {
    fun schedule(id: Long, at: Instant)
    fun cancel(id: Long)
}

class AlarmManagerReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(id: Long, at: Instant) {
        val pendingIntent = createPendingIntent(id)

        // Точность здесь важнее экономии батареи, но пропуск дозы или
        // напоминания не критичен — поэтому setExactAndAllowWhileIdle, а не
        // setAlarmClock с иконкой будильника в статус-баре.
        val exact = canScheduleExact()
        MagpieLog.i("alarm: reminder=$id at=$at exact=$exact")

        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at.toEpochMilli(),
                pendingIntent,
            )
        } else {
            // Разрешение на точные будильники отозвано вручную: лучше
            // неточное напоминание, чем никакого. Экран самодиагностики
            // (этап 7) покажет, что настройку стоит вернуть.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at.toEpochMilli(),
                pendingIntent,
            )
        }
    }

    override fun cancel(id: Long) {
        // FLAG_NO_CREATE возвращает null, если такого будильника нет —
        // отменять нечего.
        MagpieLog.i("alarm: cancel reminder=$id")
        existingPendingIntent(id)?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun createPendingIntent(id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            alarmIntent(id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun existingPendingIntent(id: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            alarmIntent(id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alarmIntent(id: Long): Intent =
        Intent(context, ReminderAlarmReceiver::class.java)
            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id)
}
