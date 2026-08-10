package com.zhukoffsky.magpie.feature.meds.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.notification.MagpieNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/** Пора принять таблетку — либо по расписанию, либо после отсрочки. */
class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduledAt = intent
            .getLongExtra(EXTRA_SCHEDULED_AT, NO_VALUE)
            .takeIf { it != NO_VALUE }
            ?.let(Instant::ofEpochMilli)

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repository = (appContext as MagpieApp).container.medRepository
                val (course, intake) = repository.onAlarm(scheduledAt) ?: return@launch

                MagpieNotifications.showDose(appContext, course, intake)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        private const val NO_VALUE = -1L
    }
}
