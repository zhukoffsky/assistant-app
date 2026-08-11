package com.zhukoffsky.magpie.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.zhukoffsky.magpie.core.ui.theme.MagpieMotion
import kotlinx.coroutines.delay

/**
 * Каскадный вход строки списка: позиция за позицией, снизу вверх.
 *
 * Анимация играет один раз на композицию, а не на каждое изменение данных.
 * Ключ у `LaunchedEffect` намеренно постоянный: иначе отметка «куплено»
 * пересобирала бы строку и она заново уезжала бы вниз при каждом тапе.
 *
 * Задержка ограничена сверху — на девятой позиции ожидание уже заметно, а
 * список к тому моменту всё равно уехал за экран.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val step = index.coerceAtMost(MagpieMotion.STAGGER_MAX_ITEMS)
        delay(step.toLong() * MagpieMotion.STAGGER_STEP_MS)
        progress.animateTo(1f, MagpieMotion.bouncy())
    }

    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28f
    }
}
