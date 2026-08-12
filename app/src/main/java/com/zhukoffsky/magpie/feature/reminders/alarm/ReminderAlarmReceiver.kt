package com.zhukoffsky.magpie.feature.reminders.alarm

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
                val reminder = repository.byId(id)
                if (reminder == null) {
                    MagpieLog.w("fired: reminder=$id missing, nothing to show")
                    return@launch
                }
                if (reminder.isDone) {
                    MagpieLog.i("fired: reminder=$id already done, skipped")
                    return@launch
                }

                val snoozed = intent.action == ACTION_SNOOZED
                MagpieLog.i("fired: reminder=$id repeat=${reminder.repeat != null} snoozed=$snoozed")

                MagpieNotifications.showReminder(appContext, reminder)

                /*
                 * Повтор двигает только настоящее срабатывание.
                 *
                 * Отсрочка идёт через этот же получатель, и без проверки
                 * действия одно нажатие «+10 мин» сдвинуло бы «каждый
                 * вторник» на неделю вперёд второй раз: первый — когда
                 * напоминание сработало по расписанию.
                 */
                if (!snoozed) repository.onFired(reminder)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** Пришло время, записанное в напоминании. */
        const val ACTION_DUE = "com.zhukoffsky.magpie.action.REMINDER_DUE"

        /** Истекла отсрочка, поставленная кнопкой в уведомлении. */
        const val ACTION_SNOOZED = "com.zhukoffsky.magpie.action.REMINDER_SNOOZED"

        const val EXTRA_REMINDER_ID = "reminderId"
        private const val INVALID_ID = -1L
    }
}
