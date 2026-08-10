package com.zhukoffsky.magpie.core.util

import android.util.Log
import com.zhukoffsky.magpie.BuildConfig

/**
 * Единая точка логирования для отладки на устройстве.
 *
 * Один тег на всё приложение, чтобы `adb logcat -s Magpie` показывал
 * связную картину: когда поставлен будильник, когда сработал, показалось ли
 * уведомление, чем закончилась синхронизация.
 *
 * **Содержимое записей не логируется** — ни тексты напоминаний, ни покупки,
 * ни тем более ключ API. Только идентификаторы, время и исходы: этого
 * хватает, чтобы понять поведение, и такой лог не страшно кому-то переслать.
 */
object MagpieLog {

    const val TAG = "Magpie"

    fun i(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, error)
    }
}
