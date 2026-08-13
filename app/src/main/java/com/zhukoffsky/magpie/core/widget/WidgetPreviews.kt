package com.zhukoffsky.magpie.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import com.zhukoffsky.magpie.core.util.MagpieLog
import com.zhukoffsky.magpie.feature.meds.widget.MedWidget
import com.zhukoffsky.magpie.feature.meds.widget.MedWidgetReceiver
import com.zhukoffsky.magpie.feature.reminders.widget.ReminderVoiceWidget
import com.zhukoffsky.magpie.feature.reminders.widget.ReminderVoiceWidgetReceiver
import com.zhukoffsky.magpie.feature.shopping.widget.ShoppingWidget
import com.zhukoffsky.magpie.feature.shopping.widget.ShoppingWidgetReceiver

/**
 * Превью виджетов в системном выборе.
 *
 * До этого там показывался `glance_default_loading_layout` — пустая
 * заглушка, одинаковая у всех трёх. Человек выбирал виджет вслепую, по
 * названию, а виджетов у нас теперь три.
 *
 * **Превью — настоящая композиция виджета, а не отдельная картинка.**
 * Glance умеет отдать `RemoteViews` наружу, и это принципиально: нарисованное
 * руками превью со временем разъезжается с тем, что виджет показывает на
 * самом деле, а такое враньё в списке выбора хуже пустой заглушки. Здесь
 * расходиться нечему — рисует тот же код и по тем же данным.
 */
object WidgetPreviews {

    /**
     * Размеры для композиции.
     *
     * Взяты из `*_widget_info.xml`, но по ширине шире минимума: превью в
     * списке показывается крупно, и на 180dp список покупок выглядел бы
     * ужатым сильнее, чем окажется на экране.
     */
    private val entries = listOf(
        Entry({ ShoppingWidget() }, ShoppingWidgetReceiver::class.java, DpSize(250.dp, 140.dp)),
        Entry({ ReminderVoiceWidget() }, ReminderVoiceWidgetReceiver::class.java, DpSize(250.dp, 70.dp)),
        Entry({ MedWidget() }, MedWidgetReceiver::class.java, DpSize(250.dp, 110.dp)),
    )

    private class Entry(
        val widget: () -> GlanceAppWidget,
        val receiver: Class<*>,
        val size: DpSize,
    )

    /**
     * Обновить превью всех трёх виджетов.
     *
     * Требует Android 15: до него `setWidgetPreview` не существует, и в
     * списке остаётся прежняя заглушка — это не поломка, а просто старое
     * поведение.
     *
     * **Система ограничивает частоту: около двух вызовов в час на всё
     * приложение.** Превью у нас три, и первая версия звала их все при
     * каждом старте процесса — то есть почти всегда упиралась в лимит и
     * тихо не делала ничего. Поэтому: при старте ставим только те, которых
     * у системы ещё нет, а все три перезаписываем лишь при смене языка
     * ([force]), когда они действительно устарели.
     *
     * @param force переписать даже то, что уже стоит.
     */
    suspend fun update(context: Context, force: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val manager = AppWidgetManager.getInstance(context)
        entries.forEach { entry ->
            val component = ComponentName(context, entry.receiver)
            val existing = runCatching {
                manager.getWidgetPreview(component, Process.myUserHandle(), CATEGORY)
            }.getOrNull()
            if (!force && existing != null) return@forEach

            runCatching { compose(context, entry) }
                .onSuccess { views ->
                    // Ответ системы обязателен к прочтению: `false` означает
                    // «не приняла, лимит». Первая версия его выбрасывала и
                    // писала в лог «поставлено» в обоих случаях — из-за чего
                    // отчёт разошёлся с тем, что видно в списке виджетов.
                    val accepted = manager.setWidgetPreview(component, CATEGORY, views)
                    MagpieLog.i("preview: ${entry.receiver.simpleName} accepted=$accepted")
                }
                .onFailure {
                    // Не повод падать: превью — украшение списка выбора, а
                    // не работа приложения.
                    MagpieLog.w("preview: ${entry.receiver.simpleName} failed", it)
                }
        }
    }

    private const val CATEGORY = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN

    @OptIn(ExperimentalGlanceApi::class)
    private suspend fun compose(context: Context, entry: Entry) =
        entry.widget().compose(context = context, size = entry.size)
}
