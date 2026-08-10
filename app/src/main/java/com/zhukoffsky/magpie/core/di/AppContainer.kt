package com.zhukoffsky.magpie.core.di

import android.content.Context
import androidx.room.Room
import com.zhukoffsky.magpie.core.data.db.MagpieDatabase
import com.zhukoffsky.magpie.feature.meds.alarm.AlarmManagerMedScheduler
import com.zhukoffsky.magpie.feature.meds.alarm.MedScheduler
import com.zhukoffsky.magpie.feature.meds.data.MedRepository
import com.zhukoffsky.magpie.feature.reminders.alarm.AlarmManagerReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.data.ReminderRepository
import com.zhukoffsky.magpie.feature.shopping.data.ShoppingRepository

/**
 * Ручной контейнер зависимостей. Живёт столько же, сколько процесс.
 *
 * Достаётся из любого места с [Context]:
 * `(context.applicationContext as MagpieApp).container`.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: MagpieDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            MagpieDatabase::class.java,
            MagpieDatabase.NAME,
        ).build()
    }

    val shoppingDao by lazy { database.shoppingDao() }
    val reminderDao by lazy { database.reminderDao() }
    val medDao by lazy { database.medDao() }

    val shoppingRepository by lazy { ShoppingRepository(shoppingDao) }

    val reminderScheduler: ReminderScheduler by lazy { AlarmManagerReminderScheduler(appContext) }
    val reminderRepository by lazy { ReminderRepository(reminderDao, reminderScheduler) }

    val medScheduler: MedScheduler by lazy { AlarmManagerMedScheduler(appContext) }
    val medRepository by lazy { MedRepository(medDao, medScheduler) }
}
