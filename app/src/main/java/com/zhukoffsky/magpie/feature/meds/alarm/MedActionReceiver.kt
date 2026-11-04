package com.zhukoffsky.magpie.feature.meds.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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
        // Кнопки уведомлений прежних сборок номера курса не несут. Угадывать
        // курс по ним нельзя: «Принял» записалось бы не тому лекарству.
        val courseId = intent
            .getLongExtra(EXTRA_COURSE_ID, NO_VALUE)
            .takeIf { it != NO_VALUE }
            ?: return

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
                MagpieNotifications.cancelDose(appContext, courseId)

                if (snoozeMinutes > 0) {
                    repository.snooze(courseId, scheduledAt, Duration.ofMinutes(snoozeMinutes))
                } else {
                    repository.markTaken(courseId, scheduledAt)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_COURSE_ID = "courseId"
        private const val EXTRA_SCHEDULED_AT = "scheduledAt"
        private const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
        private const val NO_VALUE = -1L

        fun takenIntent(context: Context, courseId: Long, scheduledAt: Instant): PendingIntent =
            pendingIntent(context, courseId, scheduledAt, snoozeMinutes = 0)

        fun snoozeIntent(
            context: Context,
            courseId: Long,
            scheduledAt: Instant,
            minutes: Long,
        ): PendingIntent = pendingIntent(context, courseId, scheduledAt, minutes)

        /**
         * Кнопки различаются **данными намерения**, а не кодом запроса.
         *
         * Пока курс был один, хватало кода запроса из минут отсрочки. Теперь
         * уведомлений столько же, сколько курсов, и без курса в данных
         * «Принял» у одного лекарства и у другого оказались бы одним
         * намерением — нажатие отметило бы не ту таблетку. Приём тот же, что
         * у кнопок напоминаний.
         */
        private fun pendingIntent(
            context: Context,
            courseId: Long,
            scheduledAt: Instant,
            snoozeMinutes: Long,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MedActionReceiver::class.java)
                .setData("magpie://dose-action/$courseId/$snoozeMinutes".toUri())
                .putExtra(EXTRA_COURSE_ID, courseId)
                .putExtra(EXTRA_SCHEDULED_AT, scheduledAt.toEpochMilli())
                .putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
