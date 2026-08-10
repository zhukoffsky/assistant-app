package com.zhukoffsky.magpie.feature.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.notification.MagpieNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Сработал будильник напоминания.
 *
 * Точка входа тонкая: достать зависимости, показать уведомление, перевести
 * повтор на следующий раз. `goAsync` держит процесс живым, пока работает
 * корутина — `onReceive` сам по себе синхронный и короткий.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, INVALID_ID)
        if (id == INVALID_ID) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repository = (appContext as MagpieApp).container.reminderRepository
                val reminder = repository.byId(id) ?: return@launch
                if (reminder.isDone) return@launch

                MagpieNotifications.showReminder(appContext, reminder)
                repository.onFired(reminder)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminderId"
        private const val INVALID_ID = -1L
    }
}
