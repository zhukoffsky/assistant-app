package com.zhukoffsky.magpie.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zhukoffsky.magpie.MagpieApp
import java.time.Duration

/** Что репозиторий может попросить у синхронизации, ничего не зная о WorkManager. */
interface SyncTrigger {
    fun requestSync()
    fun requestRemoteDelete(remoteTaskId: String)

    /** Заглушка для тестов и для сборки без синхронизации. */
    object None : SyncTrigger {
        override fun requestSync() = Unit
        override fun requestRemoteDelete(remoteTaskId: String) = Unit
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncer = (applicationContext as MagpieApp).container.remindersSyncer
        val remoteIdToDelete = inputData.getString(KEY_DELETE_REMOTE_ID)

        val outcome = if (remoteIdToDelete != null) {
            syncer.deleteRemote(remoteIdToDelete)
        } else {
            syncer.push()
        }

        return when (outcome) {
            is SyncOutcome.Retry -> Result.retry()

            // Согласие может дать только пользователь: повторять бессмысленно,
            // ошибка уже записана и видна в настройках.
            SyncOutcome.NeedsConsent,
            SyncOutcome.Disabled,
            is SyncOutcome.Success,
            -> Result.success()
        }
    }

    companion object {
        const val KEY_DELETE_REMOTE_ID = "deleteRemoteTaskId"
    }
}

class WorkManagerSyncTrigger(context: Context) : SyncTrigger {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun requestSync() {
        workManager.enqueueUniqueWork(
            UNIQUE_PUSH,
            // Пока предыдущая попытка не началась, новая её заменяет: выгружаем
            // текущее состояние, а не очередь промежуточных.
            ExistingWorkPolicy.REPLACE,
            request(Data.EMPTY),
        )
    }

    override fun requestRemoteDelete(remoteTaskId: String) {
        workManager.enqueueUniqueWork(
            "$UNIQUE_DELETE$remoteTaskId",
            ExistingWorkPolicy.KEEP,
            request(workDataOf(SyncWorker.KEY_DELETE_REMOTE_ID to remoteTaskId)),
        )
    }

    private fun request(data: Data) = OneTimeWorkRequestBuilder<SyncWorker>()
        .setInputData(data)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
        .build()

    private companion object {
        const val UNIQUE_PUSH = "sync-reminders"
        const val UNIQUE_DELETE = "sync-delete-"
    }
}
