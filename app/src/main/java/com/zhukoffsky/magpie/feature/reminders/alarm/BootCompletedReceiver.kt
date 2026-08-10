package com.zhukoffsky.magpie.feature.reminders.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhukoffsky.magpie.MagpieApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Перезагрузка и обновление приложения стирают все запланированные
 * будильники — их нужно расставить заново.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                (appContext as MagpieApp).container.reminderRepository.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
