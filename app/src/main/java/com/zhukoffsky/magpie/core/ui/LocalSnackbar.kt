package com.zhukoffsky.magpie.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

/**
 * Один хост на всё приложение.
 *
 * Отмена удаления нужна и покупкам, и напоминаниям, а Snackbar должен жить
 * в общем Scaffold — иначе он всплывал бы поверх нижней навигации или
 * дублировался на каждом экране.
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState не предоставлен")
}
