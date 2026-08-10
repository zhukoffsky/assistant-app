package com.zhukoffsky.magpie.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticCheck
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticsInspector
import com.zhukoffsky.magpie.core.diagnostics.TestAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val checks: List<DiagnosticCheck> = emptyList(),
    /** Проставляется после запуска теста, чтобы показать подсказку. */
    val testScheduled: Boolean = false,
)

class SettingsViewModel(
    private val inspector: DiagnosticsInspector,
    private val testAlarmScheduler: TestAlarmScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Перечитывает состояние разрешений. Вызывается при каждом возврате на
     * экран: пользователь уходит в системные настройки и приходит обратно,
     * и список должен показывать новое состояние, а не старое.
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(checks = inspector.inspect())
    }

    fun onTestNotification() {
        testAlarmScheduler.scheduleInAMinute()
        _uiState.value = _uiState.value.copy(testScheduled = true)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp
                SettingsViewModel(
                    inspector = app.container.diagnosticsInspector,
                    testAlarmScheduler = app.container.testAlarmScheduler,
                )
            }
        }
    }
}
