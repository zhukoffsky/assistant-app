package com.zhukoffsky.magpie.core.ui

import android.os.SystemClock
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
 *
 * **Только при открытии списка** ([entrance]). `LazyColumn` выбрасывает
 * строки, ушедшие за край, и при возврате собирает их заново; пока каскад
 * играл на каждой сборке, быстрая прокрутка длинного списка показывала
 * пустоту, в которую строки въезжали с задержкой до 360 мс, — выглядело как
 * медленная подгрузка. Найдено 4 ноября на списке из двадцати покупок.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int, entrance: ListEntrance): Modifier {
    val progress = remember { Animatable(if (entrance.admits()) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (progress.value == 1f) return@LaunchedEffect
        val step = index.coerceAtMost(MagpieMotion.STAGGER_MAX_ITEMS)
        delay(step.toLong() * MagpieMotion.STAGGER_STEP_MS)
        progress.animateTo(1f, MagpieMotion.bouncy())
    }

    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28f
    }
}

/**
 * Окно, в котором строки списка ещё въезжают каскадом. Открывается первой
 * строкой, а не экраном: данные приходят из базы позже, чем рисуется экран.
 */
class ListEntrance {
    private var openedAt = 0L

    fun admits(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (openedAt == 0L) openedAt = now
        return now - openedAt < WINDOW_MILLIS
    }

    private companion object {
        /** Весь каскад первого экрана: восемь шагов по 45 мс и запас. */
        const val WINDOW_MILLIS = 500L
    }
}

@Composable
fun rememberListEntrance(): ListEntrance = remember { ListEntrance() }
