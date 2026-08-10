package com.zhukoffsky.magpie.core.voice

/**
 * Куда попадёт надиктованное.
 *
 * Тип записи задаётся точкой входа — виджетом, плиткой, кнопкой на экране, —
 * а не содержанием фразы. Автоматической классификации в приложении нет.
 */
enum class VoiceTarget {
    SHOPPING,
    REMINDER,
    ;

    companion object {
        fun fromName(name: String?): VoiceTarget =
            entries.firstOrNull { it.name == name } ?: SHOPPING
    }
}
