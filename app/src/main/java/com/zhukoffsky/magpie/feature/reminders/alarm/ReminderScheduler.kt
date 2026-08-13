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

    /**
     * Отдельный будильник отсрочки — как у доз, и по той же причине.
     * Основной трогать нельзя: у напоминания бывает повтор, и отложенное
     * «каждый вторник» не должно сдвинуть всю серию.
     */
    fun scheduleSnooze(id: Long, at: Instant)

    /** Снимает оба: и основной будильник, и отсрочку. */
    fun cancel(id: Long)
}

class AlarmManagerReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(id: Long, at: Instant) {
        MagpieLog.i("alarm: reminder=$id at=$at")
        setAlarm(at, createPendingIntent(id, snooze = false))
    }

    override fun scheduleSnooze(id: Long, at: Instant) {
        MagpieLog.i("alarm: reminder=$id snoozed to=$at")
        setAlarm(at, createPendingIntent(id, snooze = true))
    }

    override fun cancel(id: Long) {
        // FLAG_NO_CREATE возвращает null, если такого будильника нет —
        // отменять нечего.
        MagpieLog.i("alarm: cancel reminder=$id")

        val intents = listOf(
            alarmIntent(id, snooze = false),
            alarmIntent(id, snooze = true),
            // Совместимость со сборками до 12 августа 2026: там у намерения
            // действия не было вовсе, и по нынешнему оно не находится. Такой
            // будильник остался бы висеть и сработал бы вторым — а для
            // повтора это ещё и лишний сдвиг серии. Строку можно убрать,
            // когда на всех устройствах побывает эта версия.
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id),
        )

        intents.forEach { intent ->
            PendingIntent.getBroadcast(
                context,
                id.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { pendingIntent ->
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun setAlarm(at: Instant, pendingIntent: PendingIntent) {
        // Точность здесь важнее экономии батареи, но пропуск дозы или
        // напоминания не критичен — поэтому setExactAndAllowWhileIdle, а не
        // setAlarmClock с иконкой будильника в статус-баре.
        if (canScheduleExact()) {
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

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun createPendingIntent(id: Long, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            alarmIntent(id, snooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Отсрочка отличается **действием**, а не кодом запроса.
     *
     * Extras в сравнение `PendingIntent` не входят, а действие входит — иначе
     * отсрочка и основной будильник оказались бы одним и тем же намерением, и
     * отложенное срабатывание затёрло бы будильник повтора. Заодно получатель
     * по этому же действию понимает, что повтор двигать не надо.
     */
    private fun alarmIntent(id: Long, snooze: Boolean): Intent =
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(
                if (snooze) ReminderAlarmReceiver.ACTION_SNOOZED else ReminderAlarmReceiver.ACTION_DUE,
            )
            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id)
}
