package com.zhukoffsky.magpie

import android.app.Application
import com.zhukoffsky.magpie.core.di.AppContainer
import com.zhukoffsky.magpie.core.notification.MagpieNotifications
import com.zhukoffsky.magpie.core.settings.AppearancePreferences
import com.zhukoffsky.magpie.core.settings.forLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Точка сборки зависимостей приложения.
 *
 * DI сделан вручную: [AppContainer] доступен и из Activity, и из
 * BroadcastReceiver'ов, Worker'ов и виджетов — там, где Hilt требует
 * дополнительной обвязки.
 */
class MagpieApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Живёт столько же, сколько процесс, поэтому и не отменяется. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Каналы обязаны существовать до первого уведомления, а процесс
        // нередко поднимает именно будильник — поэтому здесь синхронно и на
        // языке системы, без похода в DataStore.
        MagpieNotifications.ensureChannels(this)

        // Имя канала человек видит в системных настройках, и оно должно
        // следовать выбранному в приложении языку. Подписка, а не разовое
        // чтение: смена языка происходит при живом процессе.
        scope.launch {
            AppearancePreferences(this@MagpieApp).language
                .distinctUntilChanged()
                .collect { language ->
                    MagpieNotifications.ensureChannels(forLanguage(language))
                }
        }
    }
}
