package com.zhukoffsky.magpie.feature.reminders.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity
import com.zhukoffsky.magpie.core.voice.VoiceTarget

/**
 * Виджет-кнопка: одна большая цель для пальца, тап ведёт прямо в
 * распознавание напоминания. Списка здесь намеренно нет — виджет должен
 * попадаться под палец, а не читаться.
 */
class ReminderVoiceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(16.dp)
                        .padding(8.dp)
                        .clickable(
                            actionStartActivity(
                                VoiceCaptureActivity.intent(context, VoiceTarget.REMINDER),
                            ),
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_mic),
                        contentDescription = context.getString(R.string.widget_reminder_description),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        modifier = GlanceModifier.size(40.dp),
                    )
                    Text(
                        text = context.getString(R.string.nav_reminders),
                        style = TextStyle(color = GlanceTheme.colors.onSurface),
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

class ReminderVoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReminderVoiceWidget()
}
