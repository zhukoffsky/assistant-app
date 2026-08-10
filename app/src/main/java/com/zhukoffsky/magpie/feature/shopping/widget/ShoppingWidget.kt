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
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import androidx.glance.action.actionParametersOf
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity
import com.zhukoffsky.magpie.core.voice.VoiceTarget
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingItem

/**
 * Виджет списка покупок.
 *
 * Читает тот же поток Room, что и экран приложения, поэтому обновляется сам:
 * `updatePeriodMillis` в описании виджета стоит нулём, периодических
 * пробуждений нет.
 */
class ShoppingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as MagpieApp).container.shoppingRepository

        provideContent {
            val items by repository.observeItems().collectAsState(initial = emptyList())
            GlanceTheme {
                WidgetContent(context = context, items = items)
            }
        }
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}

@Composable
private fun WidgetContent(context: Context, items: List<ShoppingItem>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        Header(context)

        if (items.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_shopping_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(top = 8.dp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items = items, itemId = { it.id }) { item ->
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
            }
        }
    }
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
            ),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(R.drawable.ic_mic),
            contentDescription = context.getString(R.string.voice_input),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier
                .size(32.dp)
                .clickable(
                    actionStartActivity(
                        VoiceCaptureActivity.intent(context, VoiceTarget.SHOPPING),
                    ),
                ),
        )
    }
}
