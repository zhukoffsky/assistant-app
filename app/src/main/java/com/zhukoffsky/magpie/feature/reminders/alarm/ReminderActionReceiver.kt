package com.zhukoffsky.magpie.feature.reminders.alarm

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

/** Кнопка «Готово» в уведомлении о напоминании. */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, INVALID_ID)
        if (id == INVALID_ID) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                MagpieNotifications.cancel(appContext, id)
                (appContext as MagpieApp).container.reminderRepository.setDone(id, true)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminderId"
        private const val INVALID_ID = -1L

        fun donePendingIntent(context: Context, reminderId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                Intent(context, ReminderActionReceiver::class.java)
                    .putExtra(EXTRA_REMINDER_ID, reminderId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
