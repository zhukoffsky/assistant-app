package com.zhukoffsky.magpie.core.voice

/**
 * Куда попадёт надиктованное.
 *
 * Тип записи задаётся точкой входа — виджетом, плиткой, шорткатом, кнопкой
 * на экране, — а не содержанием фразы. Автоматической классификации в
 * приложении нет.
 *
 * У каждой цели свой intent-action: статические шорткаты в `shortcuts.xml`
 * умеют задавать action надёжнее, чем extra, и по нему же различаются
 * плитки в шторке.
 */
enum class VoiceTarget {
    SHOPPING,
    REMINDER,
    ;

    val action: String get() = ACTION_PREFIX + name

    companion object {
        private const val ACTION_PREFIX = "com.zhukoffsky.magpie.action.CAPTURE_"

        const val EXTRA_TARGET = "target"

        fun fromAction(action: String?): VoiceTarget? =
            entries.firstOrNull { it.action == action }

        fun fromName(name: String?): VoiceTarget? =
            entries.firstOrNull { it.name == name }
    }
}
