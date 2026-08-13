package com.zhukoffsky.magpie.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
     * Система ограничивает частоту таких обновлений, поэтому зовём редко:
     * при старте процесса и при смене языка, а не на каждую правку списка.
     * Превью — это «как выглядит виджет», а не «что в нём сейчас написано».
     */
    suspend fun update(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val manager = AppWidgetManager.getInstance(context)
        entries.forEach { entry ->
            runCatching { compose(context, entry) }
                .onSuccess { views ->
                    manager.setWidgetPreview(
                        ComponentName(context, entry.receiver),
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        views,
                    )
                    // Успех тоже пишем: увидеть превью можно только руками, в
                    // списке виджетов, а по логу хотя бы понятно, дошло ли до
                    // системы. Без этой строки молчание значит и «сработало»,
                    // и «не сработало».
                    MagpieLog.i("preview: set for ${entry.receiver.simpleName}")
                }
                .onFailure {
                    // Не повод падать: превью — украшение списка выбора, а
                    // не работа приложения.
                    MagpieLog.w("preview: ${entry.receiver.simpleName} failed", it)
                }
        }
    }

    @OptIn(ExperimentalGlanceApi::class)
    private suspend fun compose(context: Context, entry: Entry) =
        entry.widget().compose(context = context, size = entry.size)
}
