package com.zhukoffsky.magpie.feature.shopping.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.action.actionParametersOf
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.settings.ShoppingPreferences
import com.zhukoffsky.magpie.core.settings.forSelectedLanguage
import com.zhukoffsky.magpie.core.ui.theme.MagpieGlanceColors
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity
import com.zhukoffsky.magpie.core.voice.VoiceTarget
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem
import com.zhukoffsky.magpie.feature.shopping.ui.labelRes
import kotlinx.coroutines.flow.first

/**
 * Виджет списка покупок.
 *
 * Читает тот же поток Room, что и экран приложения, поэтому пока сессия
 * Glance жива, виджет обновляется сам: `updatePeriodMillis` в описании стоит
 * нулём, периодических пробуждений нет.
 */
class ShoppingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as MagpieApp).container.shoppingRepository

        /*
         * Первое значение берётся ДО `provideContent`, а не только потоком
         * внутри него.
         *
         * Раньше здесь был `collectAsState(initial = emptyList())`, и виджет
         * показывал «Покупать нечего» при полном списке. Причина в том, что
         * Glance публикует первый же кадр композиции: он успевал уйти в
         * лаунчер с пустым `initial`, пока Room отдавал данные. Сессия к тому
         * моменту заканчивалась, второго кадра не было, и лаунчер оставался с
         * пустым виджетом — даже после принудительного APPWIDGET_UPDATE.
         *
         * `first()` — приостановка: к моменту первой композиции список уже
         * настоящий. Поток при этом сохраняется, чтобы живая сессия
         * по-прежнему обновлялась сама.
         */
        val initial = repository.observeItems().first()

        // Строки — на языке приложения, а не системы: в Glance
        // `stringResource` нет, всё идёт через контекст.
        val strings = context.forSelectedLanguage()

        // Настройка читается тем же способом, что и список: виджет обязан
        // выглядеть как экран, иначе он врёт о состоянии списка.
        val preferences = ShoppingPreferences(context)
        val initialGrouped = preferences.groupByCategory.first()

        provideContent {
            val items by repository.observeItems().collectAsState(initial = initial)
            val grouped by preferences.groupByCategory.collectAsState(initial = initialGrouped)

            GlanceTheme(colors = MagpieGlanceColors) {
                WidgetContent(context = strings, items = items, grouped = grouped)
            }
        }
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}

@Composable
private fun WidgetContent(context: Context, items: List<ShoppingItem>, grouped: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            // Ни стекла, ни градиента: Glance умеет только плоскую заливку со
            // скруглением. Узнаваемость держится на тёплом фоне и акценте.
            .background(GlanceTheme.colors.background)
            .cornerRadius(28.dp)
            .padding(16.dp),
    ) {
        Header(context)

        if (items.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_shopping_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.padding(top = 10.dp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                if (grouped) {
                    /*
                     * Отделы разворачиваются в один плоский список с
                     * заголовками: у `LazyColumn` в Glance нет ни вложенных
                     * списков, ни липких заголовков — только строки.
                     *
                     * Идентификаторы заголовков берутся из отрицательного
                     * диапазона: у строк это идентификаторы записей, и
                     * пересечься с ними нельзя.
                     */
                    ShoppingCategory.entries.forEach { category ->
                        val inCategory = items.filter { it.category == category }
                        if (inCategory.isEmpty()) return@forEach

                        item(itemId = -(category.ordinal + 1).toLong()) {
                            Text(
                                text = context.getString(category.labelRes),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                ),
                                modifier = GlanceModifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(items = inCategory, itemId = { it.id }) { ItemRow(it) }
                    }
                } else {
                    items(items = items, itemId = { it.id }) { ItemRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem) {
    CheckBox(
        checked = item.isChecked,
        onCheckedChange = actionRunCallback<ToggleShoppingItemAction>(
            actionParametersOf(
                ToggleShoppingItemAction.ITEM_ID to item.id,
                ToggleShoppingItemAction.IS_CHECKED to item.isChecked,
            ),
        ),
        text = item.title,
        modifier = GlanceModifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun Header(context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.nav_shopping),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())

        // Микрофон — заливка акцентом, как кнопки в приложении. В Glance для
        // этого нужен Box с фоном: у Image своего фона нет.
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .background(GlanceTheme.colors.primary)
                .cornerRadius(14.dp)
                .clickable(
                    actionStartActivity(
                        VoiceCaptureActivity.intent(context, VoiceTarget.SHOPPING),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_mic),
                contentDescription = context.getString(R.string.voice_input),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }
}
