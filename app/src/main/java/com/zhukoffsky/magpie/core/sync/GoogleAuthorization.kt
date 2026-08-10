package com.zhukoffsky.magpie.core.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AuthorizationResult {
    data class Authorized(val accessToken: String) : AuthorizationResult

    /** Нужен экран согласия. Запускать может только активность. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthorizationResult

    data class Failed(val message: String?) : AuthorizationResult
}

/**
 * Доступ к Google Tasks через Authorization API.
 *
 * Токен запрашивается заново перед каждой синхронизацией: он живёт около
 * часа, а Play Services сами кешируют выданное согласие, поэтому повторный
 * вызов при уже выданном доступе проходит без участия пользователя.
 *
 * Идентификатор OAuth-клиента в коде не нужен: Android-клиент определяется
 * по имени пакета и SHA-1 подписи, зарегистрированным в Google Cloud
 * Console. Отсюда следствие: APK, подписанный другим ключом (например,
 * сгенерированным на раннере CI), авторизацию не пройдёт.
 */
class GoogleAuthorization(private val context: Context) : Authorizer {

    override suspend fun authorize(): AuthorizationResult {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(TASKS_SCOPE)))
            .build()

        return try {
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            val pendingIntent = result.pendingIntent
            val token = result.accessToken

            when {
                result.hasResolution() && pendingIntent != null ->
                    AuthorizationResult.NeedsConsent(pendingIntent)

                token != null -> AuthorizationResult.Authorized(token)

                else -> AuthorizationResult.Failed(null)
            }
        } catch (e: Exception) {
            AuthorizationResult.Failed(e.message)
        }
    }

    /** Разбор результата экрана согласия. */
    fun tokenFromConsent(data: Intent?): String? = runCatching {
        Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
            .accessToken
    }.getOrNull()

    companion object {
        const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
    }
}

/**
 * Мост между Task из Play Services и корутинами.
 *
 * Написан руками, чтобы не тянуть kotlinx-coroutines-play-services ради
 * одной функции.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
