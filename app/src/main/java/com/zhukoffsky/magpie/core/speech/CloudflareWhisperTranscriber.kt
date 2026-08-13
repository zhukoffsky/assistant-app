package com.zhukoffsky.magpie.core.speech

import android.util.Base64
import com.zhukoffsky.magpie.core.util.MagpieLog

/**
 * [SpeechTranscriber] поверх Workers AI.
 *
 * Ключей в коде нет: и идентификатор, и токен приходят из сборки, то есть
 * из `local.properties`, которого нет в git. Пустые — штатный случай (CI,
 * чужая машина): тогда расшифровка недоступна, и экран диктовки говорит об
 * этом прямо, вместо того чтобы молча ничего не записать.
 */
class CloudflareWhisperTranscriber(
    private val api: CloudflareWhisperApi,
    private val accountId: String,
    private val apiToken: String,
) : SpeechTranscriber {

    val isConfigured: Boolean
        get() = accountId.isNotBlank() && apiToken.isNotBlank()

    override suspend fun transcribe(audio: ByteArray, languageTag: String): String? {
        if (!isConfigured) {
            MagpieLog.w("speech: no Cloudflare credentials in the build")
            return null
        }

        val response = runCatching {
            api.run(
                account = accountId,
                model = CloudflareWhisperApi.MODEL,
                authorization = "Bearer $apiToken",
                body = WhisperRequest(
                    // NO_WRAP обязателен: перевод строки посреди значения
                    // JSON — это уже не JSON.
                    audio = Base64.encodeToString(audio, Base64.NO_WRAP),
                    language = languageTag.substringBefore('-'),
                ),
            )
        }
            .onFailure { MagpieLog.w("speech: call failed", it) }
            .getOrNull() ?: return null

        if (!response.success) {
            // Ошибку печатаем целиком: у Cloudflare тут внятные тексты вроде
            // «Authentication error», и без них неотличимо, чужой ли токен,
            // кончился ли дневной лимит или адрес не тот.
            MagpieLog.w("speech: refused, ${response.errors.joinToString { "${it.code} ${it.message}" }}")
            return null
        }

        val text = response.result?.text?.trim().orEmpty()
        if (text.isEmpty()) {
            MagpieLog.w("speech: empty transcript")
            return null
        }

        MagpieLog.i("speech: transcribed ${text.length} chars")
        return text
    }
}
