package com.zhukoffsky.magpie.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ShoppingItemEntity::class,
        ReminderEntity::class,
        MedCourseEntity::class,
        MedIntakeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(MagpieConverters::class)
abstract class MagpieDatabase : RoomDatabase() {

    abstract fun shoppingDao(): ShoppingDao

    abstract fun reminderDao(): ReminderDao

    abstract fun medDao(): MedDao

    companion object {
        const val NAME = "magpie.db"
    }
}
