package com.zhukoffsky.magpie.core.quickaccess

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.voice.tile.ReminderVoiceTileService
import com.zhukoffsky.magpie.core.voice.tile.ShoppingVoiceTileService
import com.zhukoffsky.magpie.feature.meds.widget.MedWidgetReceiver
import com.zhukoffsky.magpie.feature.reminders.widget.ReminderVoiceWidgetReceiver
import com.zhukoffsky.magpie.feature.shopping.widget.ShoppingWidgetReceiver

/**
 * Что можно поставить на экран или в шторку одним тапом.
 *
 * Смысл приложения — «тап по виджету или плитке → сразу микрофон», но до
 * самих виджета и плитки надо ещё добраться: открыть редактор лаунчера,
 * найти «Сороку» среди чужих виджетов, перетащить. Родным, кому раздан APK,
 * это приходится объяснять словами. Система умеет спросить об этом сама —
 * достаточно попросить.
 *
 * @param labelRes подпись в нашем списке.
 * @param systemLabelRes имя, которое увидит человек в системном диалоге и
 *        потом в шторке. У плиток оно своё и отличается: в списке нужно
 *        «Плитка «Покупка»», чтобы отличать строки друг от друга, а в
 *        системном диалоге — просто «Покупка», иначе получается «плитка
 *        Плитка», да ещё и обрезанная по ширине.
 */
enum class QuickAccessTarget(
    @StringRes val labelRes: Int,
    @StringRes val systemLabelRes: Int = labelRes,
) {
    ShoppingWidget(R.string.quick_shopping_widget),
    ReminderWidget(R.string.quick_reminder_widget),
    MedWidget(R.string.quick_med_widget),
    ShoppingTile(R.string.quick_shopping_tile, R.string.tile_shopping),
    ReminderTile(R.string.quick_reminder_tile, R.string.tile_reminder),
    ;

    val isWidget: Boolean get() = this in setOf(ShoppingWidget, ReminderWidget, MedWidget)
}

/**
 * @param isPlaced известно только про виджеты: система отвечает, сколько
 *        копий лежит на экранах. Про плитку такого вопроса нет — см.
 *        [QuickAccessInspector].
 */
data class QuickAccessItem(
    val target: QuickAccessTarget,
    val isPlaced: Boolean,
)

/** Состояние быстрых точек входа: что уже стоит, а что можно предложить. */
class QuickAccessInspector(private val context: Context) {

    fun inspect(): List<QuickAccessItem> = buildList {
        val manager = AppWidgetManager.getInstance(context)

        // Лаунчер вправе не уметь принимать виджеты по запросу. Тогда
        // предлагать нечего, и строки просто не показываем — кнопка, которая
        // ничего не делает, хуже её отсутствия.
        if (manager.isRequestPinAppWidgetSupported) {
            add(widgetItem(manager, QuickAccessTarget.ShoppingWidget))
            add(widgetItem(manager, QuickAccessTarget.ReminderWidget))
            add(widgetItem(manager, QuickAccessTarget.MedWidget))
        }

        // Плитки — с Android 13. Спросить систему, добавлена ли плитка,
        // нельзя вовсе: `isPlaced` у них всегда false, и предложение висит,
        // даже когда плитка уже в шторке. Повторный запрос безвреден —
        // система сама ответит «уже добавлена».
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(QuickAccessItem(QuickAccessTarget.ShoppingTile, isPlaced = false))
            add(QuickAccessItem(QuickAccessTarget.ReminderTile, isPlaced = false))
        }
    }

    private fun widgetItem(manager: AppWidgetManager, target: QuickAccessTarget) =
        QuickAccessItem(
            target = target,
            isPlaced = manager.getAppWidgetIds(component(context, target)).isNotEmpty(),
        )

    companion object {
        fun component(context: Context, target: QuickAccessTarget): ComponentName {
            val cls = when (target) {
                QuickAccessTarget.ShoppingWidget -> ShoppingWidgetReceiver::class.java
                QuickAccessTarget.ReminderWidget -> ReminderVoiceWidgetReceiver::class.java
                QuickAccessTarget.MedWidget -> MedWidgetReceiver::class.java
                QuickAccessTarget.ShoppingTile -> ShoppingVoiceTileService::class.java
                QuickAccessTarget.ReminderTile -> ReminderVoiceTileService::class.java
            }
            return ComponentName(context, cls)
        }
    }
}
