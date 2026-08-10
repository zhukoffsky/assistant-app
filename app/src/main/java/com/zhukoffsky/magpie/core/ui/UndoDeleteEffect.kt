package com.zhukoffsky.magpie.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.zhukoffsky.magpie.R

/**
 * Показывает «Удалено — Отменить» и сообщает результат обратно.
 *
 * @param deleted непустое значение означает «только что удалили вот это».
 */
@Composable
fun UndoDeleteEffect(
    deleted: Any?,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val snackbarHostState = LocalSnackbarHostState.current
    val message = stringResource(R.string.undo_deleted)
    val actionLabel = stringResource(R.string.undo_action)

    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short,
        )

        if (result == SnackbarResult.ActionPerformed) onUndo() else onDismiss()
    }
}
