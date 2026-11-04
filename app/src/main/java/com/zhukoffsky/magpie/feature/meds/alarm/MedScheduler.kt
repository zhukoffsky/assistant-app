package com.zhukoffsky.magpie.feature.meds.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.zhukoffsky.magpie.core.util.MagpieLog
import java.time.Instant

/**
 * Будильники приёма лекарства.
 *
 * У каждого курса их ровно два: ежедневный и отложенный. Раздельные
 * намерения нужны, чтобы отсрочка не затирала будильник на завтра, а
 * раздельные курсы — чтобы приём одного лекарства не затирал другой.
 */
interface MedScheduler {
    fun scheduleDaily(courseId: Long, at: Instant)
    fun scheduleSnooze(courseId: Long, at: Instant, scheduledAt: Instant)

    /** Снимает оба будильника курса: и ежедневный, и отсрочку. */
    fun cancel(courseId: Long)

    /**
     * Снять будильники сборок, где курс был один на всё приложение.
     *
     * Они ставились без идентификатора курса и по нынешним намерениям не
     * находятся: такой будильник остался бы висеть и сработал бы вторым,
     * рядом с новым. Зовётся при восстановлении будильников — оно же
     * отрабатывает сразу после обновления приложения.
     */
    fun cancelLegacy() = Unit
}

class AlarmManagerMedScheduler(private val context: Context) : MedScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun scheduleDaily(courseId: Long, at: Instant) {
        MagpieLog.i("alarm: dose course=$courseId daily at=$at")
        setAlarm(at, pendingIntent(alarmIntent(courseId, snooze = false), create = true)!!)
    }

    override fun scheduleSnooze(courseId: Long, at: Instant, scheduledAt: Instant) {
        MagpieLog.i("alarm: dose course=$courseId snooze at=$at for=$scheduledAt")
        val intent = alarmIntent(courseId, snooze = true)
            .putExtra(MedAlarmReceiver.EXTRA_SCHEDULED_AT, scheduledAt.toEpochMilli())
        setAlarm(at, pendingIntent(intent, create = true)!!)
    }

    override fun cancel(courseId: Long) {
        MagpieLog.i("alarm: cancel dose course=$courseId")
        listOf(false, true).forEach { snooze ->
            pendingIntent(alarmIntent(courseId, snooze), create = false)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    /**
     * Совместимость со сборками до ноября 2026: там у намерения не было ни
     * действия, ни данных, а различал будильники код запроса. Строку можно
     * убрать, когда на всех устройствах побывает эта версия.
     */
    override fun cancelLegacy() {
        listOf(LEGACY_REQUEST_DAILY, LEGACY_REQUEST_SNOOZE).forEach { code ->
            PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, MedAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                MagpieLog.i("alarm: cancel legacy dose alarm code=$code")
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun setAlarm(at: Instant, pendingIntent: PendingIntent) {
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at.toEpochMilli(),
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at.toEpochMilli(),
                pendingIntent,
            )
        }
    }

    /**
     * Будильники различаются **действием и данными намерения**, а не кодом
     * запроса.
     *
     * Extras в сравнение `PendingIntent` не входят, а действие и данные
     * входят. Код запроса пришлось бы складывать из идентификатора курса и
     * вида будильника — тот же приём, что у кнопок напоминаний, и по той же
     * причине.
     */
    private fun alarmIntent(courseId: Long, snooze: Boolean): Intent {
        val action = if (snooze) MedAlarmReceiver.ACTION_SNOOZED else MedAlarmReceiver.ACTION_DUE
        return Intent(context, MedAlarmReceiver::class.java)
            .setAction(action)
            .setData("magpie://dose/$courseId/$action".toUri())
            .putExtra(MedAlarmReceiver.EXTRA_COURSE_ID, courseId)
    }

    private fun pendingIntent(intent: Intent, create: Boolean): PendingIntent? {
        val flag = if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val LEGACY_REQUEST_DAILY = 1_000
        const val LEGACY_REQUEST_SNOOZE = 1_001
    }
}
