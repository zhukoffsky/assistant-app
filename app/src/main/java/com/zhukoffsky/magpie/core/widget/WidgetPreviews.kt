package com.zhukoffsky.magpie.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import androidx.core.content.edit
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
     * **И ещё раз — раз на установку и язык, а не на каждом старте.** Замер
     * 4 ноября: система отвечает `accepted=false` на каждом старте процесса,
     * а сборка трёх превью стоит около 1,4 с процессора в фоне. Превью при
     * этом — только картинка в списке выбора; то, что виджет показывает на
     * экране, обновляется своим путём (`updateAll`) и от этого не зависит.
     * Поэтому повторная попытка — только когда что-то в превью могло
     * измениться: новая установка приложения или другой язык.
     *
     * @param force переписать даже то, что уже стоит.
     * @param language выбранный язык: от него зависит текст в превью.
     */
    suspend fun update(context: Context, force: Boolean, language: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        val marker = "${installedAt(context)}:$language"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DONE, null) == marker) {
            MagpieLog.i("preview: skipped, already tried for this install and language")
            return
        }
        prefs.edit { putString(KEY_DONE, marker) }

        val manager = AppWidgetManager.getInstance(context)
        entries.forEach { entry ->
            val component = ComponentName(context, entry.receiver)
            // Читать надо не только ответ `setWidgetPreview`, но и причину, по
            // которой превью «нет». Пустой результат и брошенное исключение
            // означают разное — «система не сохранила» против «нам не дают
            // прочитать», — а `getOrNull()` их уравнивал. Из-за этого 17
            // августа выглядело так, будто превью просто не сохраняются:
            // приняты с `accepted=true`, а через шесть минут снова нет ни
            // одного.
            val existing = runCatching {
                manager.getWidgetPreview(component, Process.myUserHandle(), CATEGORY)
            }
            existing
                .onSuccess {
                    MagpieLog.i("preview: ${entry.receiver.simpleName} existing=${it != null}")
                }
                .onFailure {
                    MagpieLog.w("preview: ${entry.receiver.simpleName} read failed", it)
                }
            if (!force && existing.getOrNull() != null) return@forEach

            runCatching { compose(context, entry) }
                .onSuccess { views ->
                    // Ответ системы обязателен к прочтению: `false` означает
                    // «не приняла, лимит». Первая версия его выбрасывала и
                    // писала в лог «поставлено» в обоих случаях — из-за чего
                    // отчёт разошёлся с тем, что видно в списке виджетов.
                    val accepted = manager.setWidgetPreview(component, CATEGORY, views)

                    // `accepted` — это НЕ «сохранено». В javadoc сказано
                    // ровно одно: «true if the call was successful, false if
                    // it was rate-limited». Поэтому сразу читаем обратно, в
                    // том же процессе: так «не сохранилось вовсе»
                    // отделяется от «не пережило выход процесса», а без
                    // этого различить их нечем.
                    val readBack = runCatching {
                        manager.getWidgetPreview(component, Process.myUserHandle(), CATEGORY)
                    }.getOrNull()
                    val stored = manager.installedProviders
                        .firstOrNull { it.provider == component }
                        ?.generatedPreviewCategories
                    MagpieLog.i(
                        "preview: ${entry.receiver.simpleName} accepted=$accepted " +
                            "readBack=${readBack != null} categories=$stored",
                    )
                }
                .onFailure {
                    // Не повод падать: превью — украшение списка выбора, а
                    // не работа приложения.
                    MagpieLog.w("preview: ${entry.receiver.simpleName} failed", it)
                }
        }

        deleteComposeLeftovers(context)
    }

    /**
     * Убрать файлы раскладки, которые `compose(...)` оставляет после себя.
     *
     * Чтобы скомпоновать виджет вне лаунчера, Glance выдумывает ему id —
     * всегда отрицательный — и заводит под него файл `appWidgetLayout-<id>`
     * в `files/datastore`. Удалять его некому: виджета с таким id не
     * существует. Превью у системы не держатся (см. замеры 17 августа в
     * `CLAUDE.md`), так что собираются заново почти на каждом старте
     * процесса, и к ноябрю на телефоне скопилось 142 таких файла — по
     * три-пять в день с 13 августа.
     *
     * Файлы настоящих виджетов не трогаются: их id положительные, и имя
     * выглядит как `appWidgetLayout-34`, а не `appWidgetLayout--1013362666`.
     */
    private fun deleteComposeLeftovers(context: Context) {
        val leftovers = File(context.filesDir, "datastore")
            .listFiles { file -> file.name.startsWith(FAKE_ID_LAYOUT_PREFIX) }
            .orEmpty()
        val deleted = leftovers.count { it.delete() }
        if (leftovers.isNotEmpty()) MagpieLog.i("preview: deleted $deleted of ${leftovers.size} layout leftovers")
    }

    private const val FAKE_ID_LAYOUT_PREFIX = "appWidgetLayout--"
    private const val PREFS = "widget_previews"
    private const val KEY_DONE = "triedFor"

    /** Время установки пакета: `versionCode` у сборок один и тот же, а оно меняется с каждым APK. */
    private fun installedAt(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    private const val CATEGORY = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN

    @OptIn(ExperimentalGlanceApi::class)
    private suspend fun compose(context: Context, entry: Entry) =
        entry.widget().compose(context = context, size = entry.size)
}
