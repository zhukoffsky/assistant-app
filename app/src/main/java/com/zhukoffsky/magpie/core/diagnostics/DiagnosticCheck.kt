package com.zhukoffsky.magpie.core.diagnostics

import androidx.annotation.StringRes

/** Что открыть, чтобы починить проблему. */
enum class DiagnosticFix {
    /** Системный запрос разрешения на уведомления. */
    NOTIFICATION_PERMISSION,

    /** Настройки уведомлений приложения — там же включаются каналы. */
    NOTIFICATION_SETTINGS,

    /** Экран «Будильники и напоминания». */
    EXACT_ALARMS,

    /** Список оптимизации батареи. */
    BATTERY_OPTIMIZATION,

    /** Карточка приложения в настройках — на случай, когда всё остальное недоступно. */
    APP_SETTINGS,
}

data class DiagnosticCheck(
    val id: String,
    @StringRes val titleRes: Int,
    /** Чем грозит, если не исправить. Показывается только при проблеме. */
    @StringRes val problemRes: Int,
    val isOk: Boolean,
    val fix: DiagnosticFix?,
)
