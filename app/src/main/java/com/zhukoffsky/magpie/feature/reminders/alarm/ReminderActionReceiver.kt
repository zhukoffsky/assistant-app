package com.zhukoffsky.magpie.feature.reminders.alarm

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

/**
 * Кнопки уведомления о напоминании: «Готово» и две отсрочки.
 *
 * Android показывает не больше трёх кнопок — это и есть предел набора.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, INVALID_ID)
        if (id == INVALID_ID) return

        val snoozeMinutes = intent.getLongExtra(EXTRA_SNOOZE_MINUTES, 0)

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repository = (appContext as MagpieApp).container.reminderRepository
                MagpieNotifications.cancel(appContext, id)

                if (snoozeMinutes > 0) {
                    repository.snooze(id, Duration.ofMinutes(snoozeMinutes))
                } else {
                    repository.setDone(id, true)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminderId"
        private const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
        private const val INVALID_ID = -1L

        private const val ACTION_DONE = "com.zhukoffsky.magpie.action.REMINDER_DONE"
        private const val ACTION_SNOOZE = "com.zhukoffsky.magpie.action.REMINDER_SNOOZE"

        fun donePendingIntent(context: Context, reminderId: Long): PendingIntent =
            pendingIntent(context, reminderId, ACTION_DONE, minutes = 0)

        fun snoozePendingIntent(context: Context, reminderId: Long, minutes: Long): PendingIntent =
            pendingIntent(context, reminderId, ACTION_SNOOZE, minutes)

        /**
         * Кнопки различаются **данными намерения**, а не кодом запроса.
         *
         * Extras в сравнение `PendingIntent` не входят, поэтому две отсрочки
         * схлопнулись бы в одну — и обе кнопки делали бы одно и то же. У доз
         * та же задача решена кодом запроса, но там уведомление одно на всё
         * приложение; здесь кнопок столько же, сколько напоминаний, и код
         * пришлось бы складывать из идентификатора и минут.
         */
        private fun pendingIntent(
            context: Context,
            reminderId: Long,
            action: String,
            minutes: Long,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .setData("magpie://reminder/$reminderId/$action/$minutes".toUri())
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_SNOOZE_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
