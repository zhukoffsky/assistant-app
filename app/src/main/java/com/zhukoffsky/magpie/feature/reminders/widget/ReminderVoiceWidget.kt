package com.zhukoffsky.magpie.feature.reminders.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.theme.MagpieGlanceColors
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
            GlanceTheme(colors = MagpieGlanceColors) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clickable(
                            actionStartActivity(
                                VoiceCaptureActivity.intent(context, VoiceTarget.REMINDER),
                            ),
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    /*
                     * Подписи нет намеренно. Виджет занимает одну ячейку, а
                     * «Напоминания» в неё не влезает ни при каком кегле —
                     * на телефоне слово превращалось в «Напоми». Раз читать
                     * тут нечего, вся ячейка отдана кнопке: цель под палец
                     * получается больше, а обрезать становится нечего.
                     *
                     * Название виджета человек видит в списке при добавлении,
                     * а TalkBack читает contentDescription иконки.
                     */
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(GlanceTheme.colors.primary)
                            .cornerRadius(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_mic),
                            contentDescription = context.getString(
                                R.string.widget_reminder_description,
                            ),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                            modifier = GlanceModifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

class ReminderVoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReminderVoiceWidget()
}
