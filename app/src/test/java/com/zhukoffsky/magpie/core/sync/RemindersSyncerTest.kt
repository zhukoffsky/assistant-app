package com.zhukoffsky.magpie.core.sync

import com.zhukoffsky.magpie.core.data.db.FakeReminderDao
import com.zhukoffsky.magpie.core.data.db.ReminderEntity
import com.zhukoffsky.magpie.core.data.db.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class RemindersSyncerTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    private val dao = FakeReminderDao()
    private val api = FakeTasksApi()
    private val store = FakeSettingsStore()
    private var authorization: AuthorizationResult = AuthorizationResult.Authorized("token-1")

    private val syncer = RemindersSyncer(
        dao = dao,
        api = api,
        authorizer = object : Authorizer {
            override suspend fun authorize() = authorization
        },
        preferences = store,
        clock = Clock.fixed(now, zone),
        zone = zone,
    )

    private suspend fun addReminder(
        title: String = "позвонить",
        dueAt: Instant? = Instant.parse("2026-08-11T06:00:00Z"),
        isDone: Boolean = false,
        remoteTaskId: String? = null,
        syncState: SyncState = SyncState.PENDING_UPLOAD,
    ) = dao.insert(
        ReminderEntity(
            title = title,
            dueAt = dueAt,
            isDone = isDone,
            createdAt = now,
            updatedAt = now,
            remoteTaskId = remoteTaskId,
            syncState = syncState,
        ),
    )

    @Test
    fun `disabled sync does nothing`() = runTest {
        addReminder()

        assertEquals(SyncOutcome.Disabled, syncer.push())
        assertTrue(api.created.isEmpty())
    }

    @Test
    fun `enabling marks everything already stored for upload`() = runTest {
        addReminder(syncState = SyncState.LOCAL_ONLY)

        syncer.enable()

        assertEquals(SyncState.PENDING_UPLOAD, dao.items.value.single().syncState)
    }

    @Test
    fun `a new reminder is created remotely and remembered`() = runTest {
        store.value = SyncSettings(isEnabled = true)
        val id = addReminder()

        val outcome = syncer.push()

        assertEquals(SyncOutcome.Success(uploaded = 1), outcome)
        assertEquals("позвонить", api.created.single().second.title)
        val stored = dao.items.value.single { it.id == id }
        assertEquals("remote-1", stored.remoteTaskId)
        assertEquals(SyncState.SYNCED, stored.syncState)
    }

    @Test
    fun `an already uploaded reminder is patched, not duplicated`() = runTest {
        store.value = SyncSettings(isEnabled = true)
        addReminder(remoteTaskId = "remote-42")

        syncer.push()

        assertTrue(api.created.isEmpty())
        assertEquals("remote-42", api.patched.single().second)
    }

    @Test
    fun `a completed reminder goes over as completed`() = runTest {
        store.value = SyncSettings(isEnabled = true)
        addReminder(isDone = true)

        syncer.push()

        assertEquals(TaskDto.STATUS_COMPLETED, api.created.single().second.status)
    }

    @Test
    fun `the due date is sent as a local date at UTC midnight`() = runTest {
        store.value = SyncSettings(isEnabled = true)
        // 11 августа 09:00 по Москве — по UTC это ещё 06:00 того же дня.
        addReminder(dueAt = Instant.parse("2026-08-11T06:00:00Z"))

        syncer.push()

        assertEquals("2026-08-11T00:00:00Z", api.created.single().second.due)
    }

    @Test
    fun `a reminder without a time is sent without a due date`() = runTest {
        store.value = SyncSettings(isEnabled = true)
        addReminder(dueAt = null)

        syncer.push()

        assertNull(api.created.single().second.due)
    }

    @Test
    fun `a network failure is retryable and leaves the reminder unsynced`() = runTest {
        store.value = SyncSettings(isEnabled = true)
        addReminder()
        api.failWith = IOException("no network")

        val outcome = syncer.push()

        assertTrue(outcome is SyncOutcome.Retry)
        assertEquals(SyncState.ERROR, dao.items.value.single().syncState)
        assertEquals("no network", store.value.lastError)
    }

    @Test
    fun `a failed authorisation is retryable and does not touch the API`() = runTest {
        store.value = SyncSettings(isEnabled = true)
        addReminder()
        authorization = AuthorizationResult.Failed("token expired")

        val outcome = syncer.push()

        assertEquals(SyncOutcome.Retry("token expired"), outcome)
        assertTrue(api.created.isEmpty())
    }

    @Test
    fun `a successful run clears the previous error`() = runTest {
        store.value = SyncSettings(isEnabled = true, lastError = "old failure")
        addReminder()

        syncer.push()

        assertNull(store.value.lastError)
        assertEquals(now, store.value.lastSyncAt)
    }

    private class FakeTasksApi : GoogleTasksApi {
        val created = mutableListOf<Pair<String, TaskDto>>()
        val patched = mutableListOf<Triple<String, String, TaskDto>>()
        val deleted = mutableListOf<String>()
        var failWith: Exception? = null
        private var nextId = 1

        override suspend fun taskLists(authorization: String) = TaskListsResponse()

        override suspend fun createTask(
            authorization: String,
            listId: String,
            task: TaskDto,
        ): TaskDto {
            failWith?.let { throw it }
            created += listId to task
            return task.copy(id = "remote-${nextId++}")
        }

        override suspend fun patchTask(
            authorization: String,
            listId: String,
            taskId: String,
            task: TaskDto,
        ): TaskDto {
            failWith?.let { throw it }
            patched += Triple(listId, taskId, task)
            return task.copy(id = taskId)
        }

        override suspend fun deleteTask(authorization: String, listId: String, taskId: String) {
            failWith?.let { throw it }
            deleted += taskId
        }
    }

    private class FakeSettingsStore : SyncSettingsStore {
        private val state = MutableStateFlow(SyncSettings())

        var value: SyncSettings
            get() = state.value
            set(new) {
                state.value = new
            }

        override val settings: Flow<SyncSettings> = state

        override suspend fun current(): SyncSettings = state.value

        override suspend fun setEnabled(enabled: Boolean) {
            state.value = state.value.copy(isEnabled = enabled)
        }

        override suspend fun setTaskList(id: String) {
            state.value = state.value.copy(taskListId = id)
        }

        override suspend fun recordSuccess(at: Instant) {
            state.value = state.value.copy(lastSyncAt = at, lastError = null)
        }

        override suspend fun recordError(message: String) {
            state.value = state.value.copy(lastError = message)
        }
    }
}
