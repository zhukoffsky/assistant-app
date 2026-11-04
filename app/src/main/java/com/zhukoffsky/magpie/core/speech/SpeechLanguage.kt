package com.zhukoffsky.magpie.core.speech

import java.util.Locale

/**
 * Язык речи для распознавателей: только язык и страна, `ru-RU`.
 *
 * `Locale.getDefault().toLanguageTag()` на Android 14+ несёт ещё и
 * региональные настройки из системы — первый день недели, единицы,
 * градусы: `ru-RU-u-fw-mon-ms-metric-mu-celsius`. Распознаватель на
 * телефоне такой тег переваривает, но список скачанных моделей хранит
 * `ru-RU`, и сравнение с ним не сходилось: 4 ноября приложение просило
 * скачать модель, которая уже стояла.
 */
fun speechLanguageTag(locale: Locale = Locale.getDefault()): String =
    Locale.Builder().setLanguage(locale.language).setRegion(locale.country).build().toLanguageTag()
