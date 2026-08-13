package com.zhukoffsky.magpie.core.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Язык, снятый снаружи диалога, чтобы вернуть его внутри.
 *
 * **Зачем это нужно.** [MagpieLanguage] подменяет `LocalConfiguration` и
 * `LocalContext` в композиции — этого хватает всему, что рисуется в окне
 * активности. Диалог рисуется в **своём** окне: его содержимое компонуется
 * внутри отдельного `AbstractComposeView`, а тот в корне своей композиции
 * заново раздаёт андроидные локали из контекста собственного окна. Наши
 * значения при этом затираются, и диалог возвращается к языку системы —
 * при английском интерфейсе «Правка напоминания» открывалась по-русски,
 * вместе с датой «12 авг. 2026».
 *
 * Поэтому значения снимаются до входа в диалог и раздаются заново уже
 * внутри. Обратно к `LocaleManager` это не ведёт: пересоздания активности
 * тут нет, см. ловушки смены языка в разделе 9 `CLAUDE.md`.
 */
@Immutable
class DialogLanguage(
    private val configuration: Configuration,
    private val context: Context,
) {
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalConfiguration provides configuration,
            LocalContext provides context,
            content = content,
        )
    }
}

/** Снимает язык текущей композиции. Вызывать **вне** диалога. */
@Composable
fun rememberDialogLanguage(): DialogLanguage {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(configuration, context) { DialogLanguage(configuration, context) }
}

/**
 * `AlertDialog`, чьё содержимое говорит на языке приложения.
 *
 * Слоты оборачиваются по одному: провайдер снаружи `AlertDialog` не помогает,
 * потому что окно диалога создаётся внутри него.
 */
@Composable
fun MagpieAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val language = rememberDialogLanguage()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { language.Provide(confirmButton) },
        dismissButton = dismissButton?.let { slot -> { language.Provide(slot) } },
        title = title?.let { slot -> { language.Provide(slot) } },
        text = text?.let { slot -> { language.Provide(slot) } },
    )
}
