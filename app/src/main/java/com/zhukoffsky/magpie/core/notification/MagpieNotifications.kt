package com.zhukoffsky.magpie.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zhukoffsky.magpie.MainActivity
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.settings.forSelectedLanguage
import com.zhukoffsky.magpie.core.util.MagpieLog
import com.zhukoffsky.magpie.feature.meds.alarm.MedActionReceiver
import com.zhukoffsky.magpie.feature.meds.domain.MedCourse
import com.zhukoffsky.magpie.feature.meds.domain.MedIntake
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderActionReceiver
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder

object MagpieNotifications {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_MEDS = "meds"

    /**
     * Уведомления о приёме — по одному на курс, под общим тегом.
     *
     * Номер уведомления — id курса, а тег разводит их с напоминаниями,
     * у которых номера тоже из БД. Прежде уведомление о дозе было одно на
     * всё приложение и стояло на номере 1 без тега.
     */
    private const val DOSE_TAG = "dose"

    /**
     * Идентификаторы уведомлений о напоминаниях — это id из БД, и первое же
     * напоминание получило бы id 1 и затёрло уведомление о приёме таблетки.
     * Разводим диапазоны.
     */
    private const val REMINDER_ID_OFFSET = 1_000

    /**
     * Каналы создаются при старте процесса: повторный вызов ничего не портит.
     *
     * Имена каналов человек видит в системных настройках, поэтому [context]
     * ожидается уже с нужной локалью — см. [MagpieApp]. Повторный вызов их
     * переименовывает: `createNotificationChannel` у существующего канала
     * обновляет имя (важность — нет, её система бережёт от приложения).
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDS,
                context.getString(R.string.channel_meds),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /**
     * Уведомление о приёме лекарства.
     *
     * У каждого курса своё уведомление: два лекарства в одно время — это два
     * отдельных действия, и «Принял» у одного не должно закрывать другое.
     * Повторное уведомление того же курса замещает прежнее, а не копится.
     *
     * Приостановка — из-за языка: он лежит в DataStore, а читать его здесь, а
     * не в получателе, надёжнее. Получателей два, и второй легко забыть.
     */
    // Разрешение проверяет `canPost` строкой ниже, но lint не прослеживает
    // проверку через вызов функции и считает `notify` незащищённым.
    @SuppressLint("MissingPermission")
    suspend fun showDose(context: Context, course: MedCourse, intake: MedIntake) {
        if (!canPost(context)) {
            MagpieLog.w("notify: dose suppressed, no POST_NOTIFICATIONS")
            return
        }
        MagpieLog.i("notify: course=${course.id} dose=${intake.doseMg}mg")

        val strings = context.forSelectedLanguage()

        val notification = NotificationCompat.Builder(context, CHANNEL_MEDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.med_notification_title, course.name))
            .setContentText(strings.getString(R.string.med_notification_dose, intake.doseMg))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification,
                strings.getString(R.string.med_action_taken),
                MedActionReceiver.takenIntent(context, course.id, intake.scheduledAt),
            )
            .addAction(
                R.drawable.ic_notification,
                strings.getString(R.string.med_action_snooze_15),
                MedActionReceiver.snoozeIntent(context, course.id, intake.scheduledAt, minutes = 15),
            )
            .addAction(
                R.drawable.ic_notification,
                strings.getString(R.string.med_action_snooze_60),
                MedActionReceiver.snoozeIntent(context, course.id, intake.scheduledAt, minutes = 60),
            )
            .build()

        NotificationManagerCompat.from(context).notify(DOSE_TAG, course.id.toInt(), notification)
    }

    fun cancelDose(context: Context, courseId: Long) {
        NotificationManagerCompat.from(context).cancel(DOSE_TAG, courseId.toInt())
    }

    // Разрешение проверяет `canPost` строкой ниже, но lint не прослеживает
    // проверку через вызов функции и считает `notify` незащищённым.
    @SuppressLint("MissingPermission")
    suspend fun showReminder(context: Context, reminder: Reminder) {
        if (!canPost(context)) {
            MagpieLog.w("notify: reminder=${reminder.id} suppressed, no POST_NOTIFICATIONS")
            return
        }
        MagpieLog.i("notify: reminder=${reminder.id}")

        // Заголовок — слова самого пользователя, переводить нечего; на
        // выбранном языке нужна только подпись кнопки.
        val strings = context.forSelectedLanguage()

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification,
                strings.getString(R.string.reminder_action_done),
                ReminderActionReceiver.donePendingIntent(context, reminder.id),
            )
            // Больше трёх кнопок Android всё равно не покажет, поэтому
            // отсрочек ровно две.
            .addAction(
                R.drawable.ic_notification,
                strings.getString(R.string.reminder_action_snooze_10),
                ReminderActionReceiver.snoozePendingIntent(context, reminder.id, minutes = 10),
            )
            .addAction(
                R.drawable.ic_notification,
                strings.getString(R.string.reminder_action_snooze_60),
                ReminderActionReceiver.snoozePendingIntent(context, reminder.id, minutes = 60),
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(reminder.id), notification)
    }

    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun notificationId(reminderId: Long): Int = (reminderId + REMINDER_ID_OFFSET).toInt()

    /**
     * С Android 13 уведомления требуют разрешения. Без проверки
     * `notify` тихо ничего не делает, а мы бы считали, что напомнили.
     */
    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
