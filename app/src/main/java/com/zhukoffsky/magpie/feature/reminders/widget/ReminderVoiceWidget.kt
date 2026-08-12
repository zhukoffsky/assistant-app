package com.zhukoffsky.magpie.feature.reminders.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.MainActivity
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.settings.forSelectedLanguage
import com.zhukoffsky.magpie.core.ui.theme.MagpieGlanceColors
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity
import com.zhukoffsky.magpie.core.voice.VoiceTarget
import com.zhukoffsky.magpie.feature.reminders.domain.Reminder
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ближайшее напоминание и кнопка диктовки.
 *
 * Раньше здесь была одна кнопка во всю ячейку, но ту же работу делают
 * шорткат и плитка в шторке, а место виджет занимал как полноценный. Теперь
 * он показывает то, чего больше нигде не видно, не открывая приложение.
 */
class ReminderVoiceWidget : GlanceAppWidget() {

    /*
     * Две раскладки вместо одной: виджет мог быть положен на экран ещё
     * кнопкой, и в ячейку 1×1 текст не влезает никак — от него остаётся
     * обрезанный микрофон. Узкий размер поэтому остаётся кнопкой, а
     * ближайшее напоминание показывается с той ширины, где ему есть место.
     */
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as MagpieApp).container.reminderRepository

        /*
         * Первое значение берётся ДО `provideContent` — урок виджета покупок.
         * Glance публикует первый же скомпонованный кадр, и пустое `initial` у
         * `collectAsState` уезжает в лаунчер раньше, чем Room успевает
         * ответить: виджет показывал бы «ничего не запланировано» при полном
         * списке, причём до конца жизни процесса.
         */
        val initial = repository.observeNext().first()

        // Строки и месяц — на языке приложения, а не системы. В Glance
        // `stringResource` нет вовсе, всё идёт через контекст.
        val strings = context.forSelectedLanguage()
        val locale = strings.resources.configuration.locales[0]

        provideContent {
            val next by repository.observeNext().collectAsState(initial = initial)

            GlanceTheme(colors = MagpieGlanceColors) {
                if (LocalSize.current.width < WIDE.width) {
                    MicOnly(context = strings)
                } else {
                    WidgetContent(context = strings, locale = locale, reminder = next)
                }
            }
        }
    }

    private companion object {
        val COMPACT = DpSize(60.dp, 60.dp)
        val WIDE = DpSize(180.dp, 60.dp)
    }
}

class ReminderVoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReminderVoiceWidget()
}

/** Ячейка 1×1: читать тут нечего, вся площадь отдана под палец. */
@Composable
private fun MicOnly(context: Context) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.primary)
                .cornerRadius(24.dp)
                .clickable(
                    actionStartActivity(
                        VoiceCaptureActivity.intent(context, VoiceTarget.REMINDER),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_mic),
                contentDescription = context.getString(R.string.voice_input),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun WidgetContent(context: Context, locale: Locale, reminder: Reminder?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            // Ни стекла, ни градиента: Glance умеет только плоскую заливку со
            // скруглением. Узнаваемость держится на тёплом фоне и акценте.
            .background(GlanceTheme.colors.background)
            .cornerRadius(28.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            if (reminder == null) {
                Text(
                    text = context.getString(R.string.widget_reminder_empty),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                    ),
                )
            } else {
                Text(
                    text = reminder.title,
                    maxLines = 2,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                )
                reminder.dueAt?.let { dueAt ->
                    Text(
                        text = DateTimeFormatter
                            .ofPattern("d MMM, HH:mm", locale)
                            .format(dueAt.atZone(ZoneId.systemDefault())),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp,
                        ),
                        modifier = GlanceModifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.size(10.dp))

        // Микрофон — заливка акцентом, как кнопки в приложении. В Glance для
        // этого нужен Box с фоном: у Image своего фона нет.
        Box(
            modifier = GlanceModifier
                .size(44.dp)
                .background(GlanceTheme.colors.primary)
                .cornerRadius(16.dp)
                .clickable(
                    actionStartActivity(
                        VoiceCaptureActivity.intent(context, VoiceTarget.REMINDER),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_mic),
                contentDescription = context.getString(R.string.voice_input),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(22.dp),
            )
        }
    }
}
