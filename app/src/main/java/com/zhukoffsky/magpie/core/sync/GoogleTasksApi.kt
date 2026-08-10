package com.zhukoffsky.magpie.core.sync

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class TaskListsResponse(val items: List<TaskListDto> = emptyList())

@Serializable
data class TaskListDto(val id: String, val title: String = "")

/**
 * Задача Google Tasks. Полей у API заметно больше, но нам нужны только эти —
 * `ignoreUnknownKeys` в парсере отбрасывает остальные.
 */
@Serializable
data class TaskDto(
    val id: String? = null,
    val title: String? = null,
    val notes: String? = null,
    /** RFC3339. Google Tasks хранит только дату, время он отбрасывает. */
    val due: String? = null,
    val status: String? = null,
) {
    companion object {
        const val STATUS_NEEDS_ACTION = "needsAction"
        const val STATUS_COMPLETED = "completed"
    }
}

/**
 * Прямые вызовы REST API вместо официальной библиотеки
 * `google-api-services-tasks`: та тянет тяжёлую Java-обвязку с блокирующими
 * вызовами и своим HTTP-стеком.
 */
interface GoogleTasksApi {

    @GET("tasks/v1/users/@me/lists")
    suspend fun taskLists(@Header("Authorization") authorization: String): TaskListsResponse

    @POST("tasks/v1/lists/{list}/tasks")
    suspend fun createTask(
        @Header("Authorization") authorization: String,
        @Path("list") listId: String,
        @Body task: TaskDto,
    ): TaskDto

    @PATCH("tasks/v1/lists/{list}/tasks/{task}")
    suspend fun patchTask(
        @Header("Authorization") authorization: String,
        @Path("list") listId: String,
        @Path("task") taskId: String,
        @Body task: TaskDto,
    ): TaskDto

    // HTTP вместо DELETE: Retrofit не отдаёт Unit-ответ на @DELETE без тела
    // в некоторых версиях, а так поведение однозначно.
    @HTTP(method = "DELETE", path = "tasks/v1/lists/{list}/tasks/{task}", hasBody = false)
    suspend fun deleteTask(
        @Header("Authorization") authorization: String,
        @Path("list") listId: String,
        @Path("task") taskId: String,
    )

    companion object {
        const val BASE_URL = "https://tasks.googleapis.com/"

        /** Список по умолчанию — тот, что в Google Tasks называется «Мои задачи». */
        const val DEFAULT_LIST = "@default"
    }
}
