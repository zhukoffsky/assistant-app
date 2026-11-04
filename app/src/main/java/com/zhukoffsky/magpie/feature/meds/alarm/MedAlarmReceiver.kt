package com.zhukoffsky.magpie.feature.meds.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.util.MagpieLog
import com.zhukoffsky.magpie.core.notification.MagpieNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Пора принять таблетку — либо по расписанию, либо после отсрочки. */
class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent
            .getLongExtra(EXTRA_COURSE_ID, NO_VALUE)
            .takeIf { it != NO_VALUE }

        val scheduledAt = intent
            .getLongExtra(EXTRA_SCHEDULED_AT, NO_VALUE)
            .takeIf { it != NO_VALUE }
            ?.let(Instant::ofEpochMilli)

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repository = (appContext as MagpieApp).container.medRepository

                if (courseId == null) {
                    // Будильник сборки, где курс был один и номера не нёс.
                    // Обычно их снимает восстановление после обновления, но
                    // если какой-то дожил — расставляем всё заново, по
                    // будильнику на каждый курс.
                    MagpieLog.w("fired: legacy dose alarm without a course, rescheduling")
                    repository.rescheduleAll()
                    return@launch
                }

                val result = repository.onAlarm(courseId, scheduledAt)
                if (result == null) {
                    MagpieLog.w("fired: dose alarm for course=$courseId, nothing to show")
                    return@launch
                }

                val (course, intake) = result
                MagpieLog.i(
                    "fired: course=${course.id} dose=${intake.doseMg}mg scheduled=${intake.scheduledAt}",
                )
                MagpieNotifications.showDose(appContext, course, intake)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DUE = "com.zhukoffsky.magpie.action.DOSE_DUE"
        const val ACTION_SNOOZED = "com.zhukoffsky.magpie.action.DOSE_SNOOZED"
        const val EXTRA_COURSE_ID = "courseId"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        private const val NO_VALUE = -1L
    }
}
