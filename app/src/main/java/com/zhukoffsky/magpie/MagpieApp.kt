package com.zhukoffsky.magpie

import android.app.Application
import com.zhukoffsky.magpie.core.di.AppContainer

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

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
