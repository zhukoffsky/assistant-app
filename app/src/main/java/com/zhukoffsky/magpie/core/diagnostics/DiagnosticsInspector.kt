package com.zhukoffsky.magpie.core.diagnostics

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.speech.RecognizerIntent
import androidx.core.app.NotificationManagerCompat
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.notification.MagpieNotifications

/**
 * Проверка всего, что система может тихо запретить.
 *
 * Смысл экрана в том, что напоминание, которое не пришло, ничем не отличается
 * от напоминания, которого не было. Здесь видно, какая именно настройка
 * ломает доставку.
 */
class DiagnosticsInspector(private val context: Context) {

    fun inspect(): List<DiagnosticCheck> = buildList {
        add(notificationsCheck())
        add(channelCheck(MagpieNotifications.CHANNEL_REMINDERS, "channel_reminders", R.string.diag_channel_reminders))
        add(channelCheck(MagpieNotifications.CHANNEL_MEDS, "channel_meds", R.string.diag_channel_meds))
        add(exactAlarmsCheck())
        add(batteryCheck())
        add(recognizerCheck())
    }

    private fun notificationsCheck(): DiagnosticCheck {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        return DiagnosticCheck(
            id = "notifications",
            titleRes = R.string.diag_notifications,
            problemRes = R.string.diag_notifications_problem,
            isOk = enabled,
            // На Android 13+ уместнее системный запрос, ниже — только настройки.
            fix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                DiagnosticFix.NOTIFICATION_PERMISSION
            } else {
                DiagnosticFix.NOTIFICATION_SETTINGS
            },
        )
    }

    /**
     * Отдельный канал можно выключить, не трогая уведомления приложения
     * целиком — снаружи это выглядит как «приложение молчит без причины».
     */
    private fun channelCheck(channelId: String, id: String, titleRes: Int): DiagnosticCheck {
        val importance = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(channelId)
            ?.importance

        val blocked = importance == NotificationManager.IMPORTANCE_NONE

        return DiagnosticCheck(
            id = id,
            titleRes = titleRes,
            problemRes = R.string.diag_channel_problem,
            isOk = !blocked,
            fix = DiagnosticFix.NOTIFICATION_SETTINGS,
        )
    }

    private fun exactAlarmsCheck(): DiagnosticCheck {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        return DiagnosticCheck(
            id = "exact_alarms",
            titleRes = R.string.diag_exact_alarms,
            problemRes = R.string.diag_exact_alarms_problem,
            isOk = allowed,
            fix = DiagnosticFix.EXACT_ALARMS,
        )
    }

    private fun batteryCheck(): DiagnosticCheck {
        val ignoring = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

        return DiagnosticCheck(
            id = "battery",
            titleRes = R.string.diag_battery,
            problemRes = R.string.diag_battery_problem,
            isOk = ignoring,
            fix = DiagnosticFix.BATTERY_OPTIMIZATION,
        )
    }

    private fun recognizerCheck(): DiagnosticCheck {
        val available = context.packageManager
            .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
            .isNotEmpty()

        return DiagnosticCheck(
            id = "recognizer",
            titleRes = R.string.diag_recognizer,
            problemRes = R.string.diag_recognizer_problem,
            isOk = available,
            // Чинится установкой приложения-распознавателя, системного
            // экрана для этого нет.
            fix = null,
        )
    }
}
