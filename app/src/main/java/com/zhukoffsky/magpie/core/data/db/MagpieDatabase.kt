package com.zhukoffsky.magpie.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ShoppingItemEntity::class,
        ReminderEntity::class,
        MedCourseEntity::class,
        MedIntakeEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(MagpieConverters::class)
abstract class MagpieDatabase : RoomDatabase() {

    abstract fun shoppingDao(): ShoppingDao

    abstract fun reminderDao(): ReminderDao

    abstract fun medDao(): MedDao

    companion object {
        const val NAME = "magpie.db"

        /**
         * Отделы магазина: у покупок появилась колонка `category`.
         *
         * Миграция, а не `fallbackToDestructiveMigration`: в базе лежит
         * история приёма лекарств, терять её нельзя. Колонка добавляется
         * пустой — категорию знает только модель, и задним числом старые
         * позиции её не получают. Они собираются в «Прочее», и это
         * сознательный выбор, а не недоделка.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN category TEXT")
            }
        }

        /**
         * Срок отсрочки у напоминаний и у приёмов лекарства.
         *
         * До этого отсрочка существовала только как будильник, и
         * перезагрузка её теряла. Колонки добавляются пустыми: отсрочка —
         * состояние на ближайшие минуты, восстанавливать её задним числом
         * нечего и незачем.
         *
         * `INTEGER`, потому что `Instant` хранится миллисекундами эпохи —
         * см. `MagpieConverters`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozedUntil INTEGER")
                db.execSQL("ALTER TABLE med_intakes ADD COLUMN snoozedUntil INTEGER")
            }
        }
    }
}
