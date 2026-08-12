package com.zhukoffsky.magpie.core.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeReminderDao : ReminderDao {

    val items = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderEntity>> = items

    override fun observeNext(): Flow<ReminderEntity?> = items.map { list ->
        list.filter { !it.isDone && it.dueAt != null }
            .minWithOrNull(compareBy({ it.dueAt }, { it.id }))
    }

    override suspend fun byId(id: Long): ReminderEntity? = items.value.firstOrNull { it.id == id }

    override suspend fun pendingScheduled(): List<ReminderEntity> =
        items.value.filter { !it.isDone && it.dueAt != null }

    override suspend fun insert(reminder: ReminderEntity): Long {
        val id = nextId++
        items.value = items.value + reminder.copy(id = id)
        return id
    }

    override suspend fun setDone(id: Long, isDone: Boolean, updatedAt: Instant) {
        items.value = items.value.map {
            if (it.id == id) it.copy(isDone = isDone, updatedAt = updatedAt) else it
        }
    }

    override suspend fun setDueAt(id: Long, dueAt: Instant?, updatedAt: Instant) {
        items.value = items.value.map {
            if (it.id == id) it.copy(dueAt = dueAt, updatedAt = updatedAt) else it
        }
    }

    override suspend fun updateDetails(id: Long, title: String, dueAt: Instant?, updatedAt: Instant) {
        items.value = items.value.map {
            if (it.id == id) it.copy(title = title, dueAt = dueAt, updatedAt = updatedAt) else it
        }
    }

    override suspend fun deleteById(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun pendingSync(limit: Int): List<ReminderEntity> =
        items.value.filter { it.syncState != SyncState.SYNCED }.take(limit)

    override suspend fun setSyncState(id: Long, syncState: SyncState) {
        items.value = items.value.map { if (it.id == id) it.copy(syncState = syncState) else it }
    }

    override suspend fun setRemoteId(id: Long, remoteTaskId: String?, syncState: SyncState) {
        items.value = items.value.map {
            if (it.id == id) it.copy(remoteTaskId = remoteTaskId, syncState = syncState) else it
        }
    }

    override suspend fun markEverythingForUpload() {
        items.value = items.value.map {
            if (it.syncState == SyncState.LOCAL_ONLY) it.copy(syncState = SyncState.PENDING_UPLOAD) else it
        }
    }
}
