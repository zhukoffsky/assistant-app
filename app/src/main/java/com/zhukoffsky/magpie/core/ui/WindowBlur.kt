package com.zhukoffsky.magpie.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import java.util.function.Consumer

/*
 * Размытие задника у прозрачного окна.
 *
 * Это единственное место во всём приложении, где размытие вообще достижимо.
 * В Compose нет backdrop-blur: `Modifier.blur` размывает собственное
 * содержимое элемента, а не то, что лежит под ним, — поэтому стеклянные
 * карточки внутри экранов остаются заливкой на любой версии Android
 * (см. комментарий в `MagpieSurfaces.kt`). Размыть чужое содержимое умеет
 * только окно целиком, и ровно такой случай — прозрачная
 * `VoiceCaptureActivity` поверх списка.
 *
 * Виджетам Glance это тоже недоступно: они рисуются в процессе лаунчера
 * через RemoteViews.
 */

/**
 * Включает размытие всего, что видно за окном, и сообщает, работает ли оно
 * прямо сейчас.
 *
 * Возвращает `false` — значит, размытия нет и рисовать нужно запасной
 * вариант (плотное притемнение).
 *
 * **Проверять версию SDK недостаточно.** Даже на Android 12+ система гасит
 * размытие при включённом энергосбережении, при взведённом девелоперском
 * флаге «отключить размытие окон» и на устройствах, где его не поддерживает
 * SurfaceFlinger. Причём гасит **на ходу** — экономия батареи может
 * включиться, пока экран открыт, — поэтому здесь слушатель, а не разовый
 * опрос: иначе на полпути осталась бы прозрачная карточка без фона.
 */
@Composable
fun blurBehindWindow(radius: Dp): Boolean {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Контекст композиции обёрнут в `MagpieLanguage`, поэтому до активности
    // приходится разворачивать цепочку `ContextWrapper`.
    val activity = remember(context) { context.findActivity() }
    val radiusPx = with(density) { radius.roundToPx() }

    var enabled by remember { mutableStateOf(false) }

    DisposableEffect(activity, radiusPx) {
        val window = activity?.window
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return@DisposableEffect onDispose { }
        }

        val detach = activity.enableBlurBehind(radiusPx) { enabled = it }
        onDispose {
            detach()
            enabled = false
        }
    }

    return enabled
}

/**
 * Ставит флаг размытия и подписывается на его отключение системой.
 *
 * Вынесено отдельной функцией с [RequiresApi], чтобы проверка версии была
 * видна анализатору: внутри лямбды `DisposableEffect` он её не замечает.
 *
 * @return снятие подписки и флага.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun Activity.enableBlurBehind(radiusPx: Int, onChanged: (Boolean) -> Unit): () -> Unit {
    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    window.attributes = window.attributes.also { it.blurBehindRadius = radiusPx }

    // Слушатель вызывается сразу при подписке текущим значением, так что
    // отдельный опрос `isCrossWindowBlurEnabled` не нужен.
    val listener = Consumer<Boolean> { onChanged(it) }
    windowManager.addCrossWindowBlurEnabledListener(listener)

    return {
        windowManager.removeCrossWindowBlurEnabledListener(listener)
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
