package com.zhukoffsky.magpie.feature.settings.ui

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticCheck
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticsInspector
import com.zhukoffsky.magpie.core.diagnostics.TestAlarmScheduler
import com.zhukoffsky.magpie.core.settings.AppLanguage
import com.zhukoffsky.magpie.core.settings.AppearancePreferences
import com.zhukoffsky.magpie.core.settings.ShoppingPreferences
import com.zhukoffsky.magpie.core.settings.ThemeMode
import com.zhukoffsky.magpie.core.sync.AuthorizationResult
import com.zhukoffsky.magpie.core.sync.RemindersSyncer
import com.zhukoffsky.magpie.core.sync.SyncSettings
import com.zhukoffsky.magpie.core.sync.SyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val checks: List<DiagnosticCheck> = emptyList(),
    /** Проставляется после запуска теста, чтобы показать подсказку. */
    val testScheduled: Boolean = false,
)

class SettingsViewModel(
    private val inspector: DiagnosticsInspector,
    private val testAlarmScheduler: TestAlarmScheduler,
    private val syncer: RemindersSyncer,
    private val syncTrigger: SyncTrigger,
    private val appearance: AppearancePreferences,
    private val shopping: ShoppingPreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = appearance.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ThemeMode.SYSTEM,
    )

    val language: StateFlow<AppLanguage> = appearance.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AppLanguage.SYSTEM,
    )

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { appearance.setThemeMode(mode) }
    }

    val groupByCategory: StateFlow<Boolean> = shopping.groupByCategory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = false,
    )

    fun onGroupByCategoryChange(enabled: Boolean) {
        viewModelScope.launch { shopping.setGroupByCategory(enabled) }
    }

    fun onLanguageSelected(language: AppLanguage) {
        viewModelScope.launch { appearance.setLanguage(language) }
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val syncSettings: StateFlow<SyncSettings> = syncer.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SyncSettings(),
    )

    /** Экран согласия Google. Запустить его может только активность. */
    private val _consentRequest = MutableStateFlow<PendingIntent?>(null)
    val consentRequest: StateFlow<PendingIntent?> = _consentRequest.asStateFlow()

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

    fun onConnectGoogle() {
        viewModelScope.launch {
            syncer.enable()
            authorizeThenSync()
        }
    }

    /**
     * Спрашивает доступ и, если нужно согласие, поднимает системный экран.
     *
     * Запустить его может только активность, поэтому пройти этот шаг из
     * фоновой задачи невозможно в принципе: `WorkManager` упирается в
     * `consent_required`, записывает ошибку и завершается — и так каждый раз.
     * Отсюда правило: очередь ставится **после** того, как доступ получен, а
     * не вместо этого.
     */
    private suspend fun authorizeThenSync() {
        when (val result = syncer.authorize()) {
            is AuthorizationResult.Authorized -> syncTrigger.requestSync()
            is AuthorizationResult.NeedsConsent -> _consentRequest.value = result.pendingIntent
            is AuthorizationResult.Failed -> Unit // текст ошибки покажет следующая попытка
        }
    }

    fun onConsentHandled(granted: Boolean) {
        _consentRequest.value = null
        if (granted) syncTrigger.requestSync()
    }

    /**
     * «Синхронизировать сейчас» раньше просто ставила задачу в очередь. Если
     * согласие ещё не выдано, задача его не выспросит — экран согласия ей
     * недоступен, — поэтому кнопка молча возвращала `consent_required` и
     * ничего не меняла, сколько её ни нажимай.
     */
    fun onSyncNow() {
        viewModelScope.launch { authorizeThenSync() }
    }

    fun onDisconnectGoogle() {
        viewModelScope.launch { syncer.disable() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MagpieApp
                val container = app.container
                SettingsViewModel(
                    inspector = container.diagnosticsInspector,
                    testAlarmScheduler = container.testAlarmScheduler,
                    syncer = container.remindersSyncer,
                    syncTrigger = container.syncTrigger,
                    // Мимо AppContainer: DataStore всё равно один на процесс,
                    // так что дублирования не возникает.
                    appearance = AppearancePreferences(app),
                    shopping = ShoppingPreferences(app),
                )
            }
        }
    }
}
