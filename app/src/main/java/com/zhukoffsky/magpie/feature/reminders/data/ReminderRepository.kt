package com.zhukoffsky.magpie.feature.reminders.data

import com.zhukoffsky.magpie.core.data.db.ReminderDao
import com.zhukoffsky.magpie.core.data.db.ReminderEntity
import com.zhukoffsky.magpie.core.data.db.SyncState
import com.zhukoffsky.magpie.core.sync.SyncTrigger
import com.zhukoffsky.magpie.feature.reminders.alarm.ReminderScheduler
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder
import com.zhukoffsky.magpie.feature.reminders.domain.RepeatRule
import com.zhukoffsky.magpie.feature.reminders.domain.nextAfter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val syncTrigger: SyncTrigger = SyncTrigger.None,
) {

    fun observeAll(): Flow<List<Reminder>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun byId(id: Long): Reminder? = dao.byId(id)?.toDomain()

    suspend fun add(title: String, dueAt: Instant?, repeat: RepeatRule?): Long? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null

        val now = clock.instant()
        val id = dao.insert(
            ReminderEntity(
                title = cleanTitle,
                dueAt = dueAt,
                repeatRule = repeat?.serialize(),
                createdAt = now,
                updatedAt = now,
                syncState = SyncState.PENDING_UPLOAD,
            ),
        )
        if (dueAt != null) scheduler.schedule(id, dueAt)
        syncTrigger.requestSync()
        return id
    }

    /**
     * Правка заголовка и времени вручную — единственный способ починить
     * ошибку разбора фразы, кроме удаления и переделки.
     */
    suspend fun update(id: Long, title: String, dueAt: Instant?) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return

        dao.updateDetails(id, cleanTitle, dueAt, clock.instant())

        if (dueAt != null) scheduler.schedule(id, dueAt) else scheduler.cancel(id)
        markForUpload(id)
    }

    suspend fun setDone(id: Long, isDone: Boolean) {
        dao.setDone(id, isDone, clock.instant())
        val reminder = dao.byId(id)?.toDomain() ?: return

        if (isDone) {
            scheduler.cancel(id)
        } else {
            reminder.dueAt?.let { scheduler.schedule(id, it) }
        }

        markForUpload(id)
    }

    suspend fun delete(id: Long) {
        // Идентификатор в Google нужно забрать до удаления строки: после
        // него узнать, что удалять на той стороне, будет неоткуда.
        val remoteTaskId = dao.byId(id)?.remoteTaskId

        scheduler.cancel(id)
        dao.deleteById(id)

        remoteTaskId?.let(syncTrigger::requestRemoteDelete)
    }

    /**
     * Вызывается после срабатывания будильника.
     *
     * Повторяющееся напоминание переводится на следующее срабатывание,
     * одноразовое остаётся в списке просроченным — пока пользователь сам не
     * отметит его выполненным.
     */
    suspend fun onFired(reminder: Reminder) {
        val repeat = reminder.repeat ?: return
        val dueAt = reminder.dueAt ?: return

        val next = repeat.nextAfter(
            after = clock.instant().atZone(clock.zone),
            timeOfDay = dueAt.atZone(clock.zone),
        ).toInstant()

        dao.setDueAt(reminder.id, next, clock.instant())
        scheduler.schedule(reminder.id, next)
        markForUpload(reminder.id)
    }

    private suspend fun markForUpload(id: Long) {
        dao.setSyncState(id, SyncState.PENDING_UPLOAD)
        syncTrigger.requestSync()
    }

    /**
     * Восстановление будильников после перезагрузки или обновления
     * приложения: система забывает все запланированные alarm'ы.
     */
    suspend fun rescheduleAll() {
        val now = clock.instant()

        dao.pendingScheduled().forEach { entity ->
            val reminder = entity.toDomain()
            val dueAt = reminder.dueAt ?: return@forEach

            when {
                dueAt.isAfter(now) -> scheduler.schedule(reminder.id, dueAt)

                // Повтор, чьё время прошло, пока телефон был выключен:
                // переводим на ближайшее будущее срабатывание.
                reminder.repeat != null -> onFired(reminder)

                // Пропущенное одноразовое остаётся просроченным в списке;
                // будить пользователя задним числом смысла нет.
                else -> Unit
            }
        }
    }
}

private fun ReminderEntity.toDomain() = Reminder(
    id = id,
    title = title,
    dueAt = dueAt,
    repeat = RepeatRule.parse(repeatRule),
    isDone = isDone,
)
