package com.zhukoffsky.magpie.core.sync

import com.zhukoffsky.magpie.core.data.db.ReminderDao
import com.zhukoffsky.magpie.core.data.db.ReminderEntity
import com.zhukoffsky.magpie.core.data.db.SyncState
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

sealed interface SyncOutcome {
    /** Синхронизация выключена — работать нечего. */
    data object Disabled : SyncOutcome

    data class Success(val uploaded: Int) : SyncOutcome

    /** Доступ отозван или не выдан: нужен экран согласия, повторять смысла нет. */
    data object NeedsConsent : SyncOutcome

    /** Временная неудача — сеть, 5xx. Имеет смысл повторить позже. */
    data class Retry(val reason: String) : SyncOutcome
}

/** Абстракция ради тестов: настоящая реализация ходит в Play Services. */
interface Authorizer {
    suspend fun authorize(): AuthorizationResult
}

/**
 * Односторонняя выгрузка напоминаний в Google Tasks.
 *
 * Обратное чтение не делается сознательно: двусторонняя синхронизация — это
 * разрешение конфликтов и удаления-призраки, то есть отдельный проект по
 * объёму. Здесь Google Tasks — витрина, а не источник правды.
 */
class RemindersSyncer(
    private val dao: ReminderDao,
    private val api: GoogleTasksApi,
    private val authorizer: Authorizer,
    private val preferences: SyncSettingsStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    val settings: Flow<SyncSettings> get() = preferences.settings

    suspend fun authorize(): AuthorizationResult = authorizer.authorize()

    /**
     * Включение синхронизации помечает к выгрузке всё, что накопилось до
     * этого момента: иначе в Google уехали бы только новые напоминания, а
     * старые остались бы невидимыми навсегда.
     */
    suspend fun enable() {
        preferences.setEnabled(true)
        dao.markEverythingForUpload()
    }

    suspend fun disable() = preferences.setEnabled(false)

    suspend fun push(): SyncOutcome {
        val settings = preferences.current()
        if (!settings.isEnabled) return SyncOutcome.Disabled

        val token = when (val result = authorizer.authorize()) {
            is AuthorizationResult.Authorized -> result.accessToken
            is AuthorizationResult.NeedsConsent -> {
                preferences.recordError(CONSENT_REQUIRED)
                return SyncOutcome.NeedsConsent
            }

            is AuthorizationResult.Failed -> {
                val reason = result.message ?: UNKNOWN_ERROR
                preferences.recordError(reason)
                return SyncOutcome.Retry(reason)
            }
        }

        val pending = dao.pendingSync(limit = BATCH_SIZE)
        var uploaded = 0

        for (reminder in pending) {
            val outcome = upload(reminder, token, settings.taskListId)
            if (outcome != null) {
                // Первая же неудача останавливает проход: если отвалилась
                // сеть, остальные попытки отвалятся тем же способом, только
                // потратят время и трафик.
                dao.setSyncState(reminder.id, SyncState.ERROR)
                preferences.recordError(outcome)
                return SyncOutcome.Retry(outcome)
            }
            uploaded++
        }

        preferences.recordSuccess(clock.instant())
        return SyncOutcome.Success(uploaded)
    }

    /** @return null при успехе, иначе текст ошибки. */
    private suspend fun upload(reminder: ReminderEntity, token: String, listId: String): String? {
        val body = TaskDto(
            title = reminder.title,
            due = reminder.dueAt?.let { dueAt ->
                // Google Tasks хранит только дату: время в поле due
                // отбрасывается на их стороне. Напоминание в приложении
                // всё равно сработает в точное время — в Tasks оно просто
                // выглядит как задача на день.
                dueAt.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).format(RFC_3339)
            },
            status = if (reminder.isDone) TaskDto.STATUS_COMPLETED else TaskDto.STATUS_NEEDS_ACTION,
        )

        return try {
            val remoteId = reminder.remoteTaskId
            val remote = if (remoteId == null) {
                api.createTask("Bearer $token", listId, body)
            } else {
                api.patchTask("Bearer $token", listId, remoteId, body)
            }

            dao.setRemoteId(reminder.id, remote.id ?: remoteId, SyncState.SYNCED)
            null
        } catch (e: Exception) {
            e.message ?: UNKNOWN_ERROR
        }
    }

    /**
     * Удаление задачи в Google после удаления напоминания локально.
     * Запись в БД к этому моменту уже не существует, поэтому идентификатор
     * приходит снаружи.
     */
    suspend fun deleteRemote(remoteTaskId: String): SyncOutcome {
        val settings = preferences.current()
        if (!settings.isEnabled) return SyncOutcome.Disabled

        val token = when (val result = authorizer.authorize()) {
            is AuthorizationResult.Authorized -> result.accessToken
            is AuthorizationResult.NeedsConsent -> return SyncOutcome.NeedsConsent
            is AuthorizationResult.Failed -> return SyncOutcome.Retry(result.message ?: UNKNOWN_ERROR)
        }

        return try {
            api.deleteTask("Bearer $token", settings.taskListId, remoteTaskId)
            SyncOutcome.Success(uploaded = 0)
        } catch (e: Exception) {
            SyncOutcome.Retry(e.message ?: UNKNOWN_ERROR)
        }
    }

    /** Список задач для выбора в настройках. */
    suspend fun taskLists(): List<TaskListDto> {
        val token = (authorizer.authorize() as? AuthorizationResult.Authorized)?.accessToken
            ?: return emptyList()

        return runCatching { api.taskLists("Bearer $token").items }.getOrDefault(emptyList())
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val CONSENT_REQUIRED = "consent_required"
        const val UNKNOWN_ERROR = "unknown_error"
        val RFC_3339: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    }
}
