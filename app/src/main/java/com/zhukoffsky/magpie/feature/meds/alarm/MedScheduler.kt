package com.zhukoffsky.magpie.feature.meds.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zhukoffsky.magpie.core.util.MagpieLog
import java.time.Instant

/**
 * Будильники приёма лекарства.
 *
 * Их ровно два: ежедневный и отложенный. Раздельные коды запроса нужны,
 * чтобы отсрочка не затирала будильник на завтра.
 */
interface MedScheduler {
    fun scheduleDaily(at: Instant)
    fun scheduleSnooze(at: Instant, scheduledAt: Instant)
    fun cancelAll()
}

class AlarmManagerMedScheduler(private val context: Context) : MedScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun scheduleDaily(at: Instant) {
        MagpieLog.i("alarm: dose daily at=$at")
        setAlarm(at, pendingIntent(REQUEST_DAILY, scheduledAt = null, create = true)!!)
    }

    override fun scheduleSnooze(at: Instant, scheduledAt: Instant) {
        MagpieLog.i("alarm: dose snooze at=$at for=$scheduledAt")
        setAlarm(at, pendingIntent(REQUEST_SNOOZE, scheduledAt, create = true)!!)
    }

    override fun cancelAll() {
        listOf(REQUEST_DAILY, REQUEST_SNOOZE).forEach { code ->
            pendingIntent(code, scheduledAt = null, create = false)?.let {
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

    private fun pendingIntent(requestCode: Int, scheduledAt: Instant?, create: Boolean): PendingIntent? {
        val intent = Intent(context, MedAlarmReceiver::class.java).apply {
            scheduledAt?.let { putExtra(MedAlarmReceiver.EXTRA_SCHEDULED_AT, it.toEpochMilli()) }
        }
        val flag = if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_DAILY = 1_000
        const val REQUEST_SNOOZE = 1_001
    }
}
