package com.zhukoffsky.magpie.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/*
 * Движение задаётся пружинами, а не кривыми: длительность не назначается,
 * её определяет физика. Три пружины на всё приложение — больше не нужно, а
 * каждая лишняя потом расползается по экранам в виде «почти таких же».
 */
object MagpieMotion {

    /** Чекбокс, кнопки, отметка дозы — отклик без раскачки. */
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.90f,
        stiffness = 420f,
    )

    /** Появление карточки диктовки и FAB — заметный, но короткий отскок. */
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.62f,
        stiffness = 300f,
    )

    /** Смена вкладки и фон — медленно и без колебаний. */
    fun <T> soft() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 180f,
    )

    /** Простое затухание там, где пружина неуместна. */
    fun <T> fade() = tween<T>(durationMillis = 180)

    /** Задержка каскада на позицию списка. */
    const val STAGGER_STEP_MS = 45

    /**
     * Дальше задержку не наращиваем: на девятой позиции ожидание уже заметно,
     * а список к тому моменту всё равно уехал за экран.
     */
    const val STAGGER_MAX_ITEMS = 8
}
