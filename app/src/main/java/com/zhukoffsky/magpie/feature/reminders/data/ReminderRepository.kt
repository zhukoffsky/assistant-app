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
import java.time.Duration
import java.time.Instant

/**
 * [onChanged] вызывается после каждой записи и служит одному: растолкать
 * виджет. Причина та же, что у списка покупок: подписка на поток Room
 * обновляет виджет, только пока жива сессия Glance, а она заканчивается
 * вместе с процессом. Хук лежит здесь, потому что писать в напоминания умеют
 * ещё голосовой ввод и уведомление, и точку записи легко забыть.
 * Android-зависимостей у репозитория при этом не появляется: чем именно
 * «растолкать» — решает `AppContainer`.
 */
class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val syncTrigger: SyncTrigger = SyncTrigger.None,
    private val onChanged: suspend () -> Unit = {},
) {

    fun observeAll(): Flow<List<Reminder>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Ближайшее невыполненное — для виджета. */
    fun observeNext(): Flow<Reminder?> = dao.observeNext().map { it?.toDomain() }

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
        onChanged()
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

        // Сначала снимаются оба будильника: у напоминания могла висеть
        // отсрочка, и после правки времени она сработала бы по старому.
        // Вместе с будильником стирается и её срок — иначе перезагрузка
        // вернула бы отсрочку, отменённую правкой.
        scheduler.cancel(id)
        dao.setSnoozedUntil(id, null)
        if (dueAt != null) scheduler.schedule(id, dueAt)

        markForUpload(id)
        onChanged()
    }

    /**
     * Отложить: отдельный будильник **и** срок в базе.
     *
     * Времени напоминания отсрочка не трогает — иначе отложенное «каждый
     * вторник» сдвинуло бы всю серию. А срок пишется отдельной колонкой
     * (`snoozedUntil`), чтобы пережить перезагрузку: раньше отсрочка была
     * только будильником, и выключенный телефон её терял.
     */
    suspend fun snooze(id: Long, delay: Duration) {
        val reminder = dao.byId(id)?.toDomain() ?: return
        if (reminder.isDone) return

        val at = clock.instant().plus(delay)
        // Сначала в базу, потом будильник: перезагрузка между этими двумя
        // строками потеряет будильник, но не сам факт отсрочки, и он
        // вернётся при следующей загрузке. Обратный порядок терял бы всё.
        dao.setSnoozedUntil(id, at)
        scheduler.scheduleSnooze(id, at)
    }

    /**
     * Отложенный показ состоялся — срок больше не нужен.
     *
     * Без этого он остался бы в базе навсегда и всплыл бы после ближайшей
     * перезагрузки: уведомление вернулось бы за прошлый срок.
     */
    suspend fun onSnoozeFired(id: Long) = dao.setSnoozedUntil(id, null)

    suspend fun setDone(id: Long, isDone: Boolean) {
        dao.setDone(id, isDone, clock.instant())
        val reminder = dao.byId(id)?.toDomain() ?: return

        if (isDone) {
            scheduler.cancel(id)
            // Выполненное не должно вернуться отсрочкой после перезагрузки.
            dao.setSnoozedUntil(id, null)
        } else {
            reminder.dueAt?.let { scheduler.schedule(id, it) }
        }

        markForUpload(id)
        onChanged()
    }

    suspend fun delete(id: Long) {
        // Идентификатор в Google нужно забрать до удаления строки: после
        // него узнать, что удалять на той стороне, будет неоткуда.
        val remoteTaskId = dao.byId(id)?.remoteTaskId

        scheduler.cancel(id)
        dao.deleteById(id)

        remoteTaskId?.let(syncTrigger::requestRemoteDelete)
        onChanged()
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
        onChanged()
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

            // Сначала снять всё, что могло остаться: после перезагрузки не
            // остаётся ничего, а после обновления приложения — будильники
            // прежней сборки, которые сработали бы вторыми.
            scheduler.cancel(reminder.id)

            when {
                dueAt.isAfter(now) -> scheduler.schedule(reminder.id, dueAt)

                // Повтор, чьё время прошло, пока телефон был выключен:
                // переводим на ближайшее будущее срабатывание.
                reminder.repeat != null -> onFired(reminder)

                // Пропущенное одноразовое остаётся просроченным в списке;
                // будить пользователя задним числом смысла нет.
                else -> Unit
            }

            restoreSnooze(entity.id, entity.snoozedUntil, now)
        }
    }

    /**
     * Вернуть отложенный будильник после перезагрузки.
     *
     * Отдельно от основного и **после** него: у повтора это два разных
     * будильника, и отсрочка не имеет права сдвинуть серию — то же
     * рассуждение, по которому они изначально различаются действием
     * намерения, а не кодом запроса.
     *
     * Просроченную отсрочку не восстанавливаем, а стираем: телефон мог
     * пролежать выключенным до утра, и уведомление «отложено на десять
     * минут», пришедшее к завтраку, — это не напоминание, а недоумение.
     */
    private suspend fun restoreSnooze(id: Long, snoozedUntil: Instant?, now: Instant) {
        val at = snoozedUntil ?: return
        if (at.isAfter(now)) scheduler.scheduleSnooze(id, at) else dao.setSnoozedUntil(id, null)
    }
}

private fun ReminderEntity.toDomain() = Reminder(
    id = id,
    title = title,
    dueAt = dueAt,
    repeat = RepeatRule.parse(repeatRule),
    isDone = isDone,
)
