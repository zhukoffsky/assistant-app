package com.zhukoffsky.magpie.feature.meds.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.notification.MagpieNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Кнопки уведомления о приёме.
 *
 * Android показывает не больше трёх кнопок, поэтому здесь только «Принял»,
 * «+15 мин» и «+60 мин». Полный набор отсрочек (5/10/15/30/60) живёт на
 * экране приложения.
 */
class MedActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduledAt = intent
            .getLongExtra(EXTRA_SCHEDULED_AT, NO_VALUE)
            .takeIf { it != NO_VALUE }
            ?.let(Instant::ofEpochMilli)
            ?: return

        val snoozeMinutes = intent.getLongExtra(EXTRA_SNOOZE_MINUTES, 0)

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repository = (appContext as MagpieApp).container.medRepository
                MagpieNotifications.cancelDose(appContext)

                if (snoozeMinutes > 0) {
                    repository.snooze(scheduledAt, Duration.ofMinutes(snoozeMinutes))
                } else {
                    repository.markTaken(scheduledAt)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_SCHEDULED_AT = "scheduledAt"
        private const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
        private const val NO_VALUE = -1L

        fun takenIntent(context: Context, scheduledAt: Instant): PendingIntent =
            pendingIntent(context, scheduledAt, snoozeMinutes = 0)

        fun snoozeIntent(context: Context, scheduledAt: Instant, minutes: Long): PendingIntent =
            pendingIntent(context, scheduledAt, minutes)

        private fun pendingIntent(
            context: Context,
            scheduledAt: Instant,
            snoozeMinutes: Long,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            // Код запроса различает кнопки: иначе три PendingIntent'а
            // схлопнулись бы в один и все кнопки делали бы одно и то же.
            snoozeMinutes.toInt(),
            Intent(context, MedActionReceiver::class.java)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt.toEpochMilli())
                .putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
