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
import com.zhukoffsky.magpie.core.settings.ThemeMode
import com.zhukoffsky.magpie.core.sync.AuthorizationResult
import com.zhukoffsky.magpie.core.sync.RemindersSyncer
import com.zhukoffsky.magpie.core.sync.SyncSettings
import com.zhukoffsky.magpie.core.sync.SyncTrigger
import androidx.glance.appwidget.updateAll
import com.zhukoffsky.magpie.feature.reminders.widget.ReminderVoiceWidget
import com.zhukoffsky.magpie.feature.shopping.widget.ShoppingWidget
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
    /** Пересборка виджетов. Лямбдой, чтобы не тащить `Context` во ViewModel. */
    private val refreshWidgets: suspend () -> Unit,
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

    /**
     * Тема применяется и к виджетам, но сами они об этом не узнают: подписка
     * на настройки живёт внутри сессии Glance, а она заканчивается вместе с
     * процессом. Поэтому после записи виджеты пересобираются явно — иначе на
     * домашнем экране осталась бы прежняя тема до следующего изменения
     * списка или перезагрузки.
     */
    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch {
            appearance.setThemeMode(mode)
            refreshWidgets()
        }
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

            when (val result = syncer.authorize()) {
                is AuthorizationResult.Authorized -> syncTrigger.requestSync()
                is AuthorizationResult.NeedsConsent -> _consentRequest.value = result.pendingIntent
                is AuthorizationResult.Failed -> Unit // текст ошибки покажет следующая попытка
            }
        }
    }

    fun onConsentHandled(granted: Boolean) {
        _consentRequest.value = null
        if (granted) syncTrigger.requestSync()
    }

    fun onSyncNow() = syncTrigger.requestSync()

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
                    refreshWidgets = {
                        ShoppingWidget().updateAll(app)
                        ReminderVoiceWidget().updateAll(app)
                    },
                )
            }
        }
    }
}
