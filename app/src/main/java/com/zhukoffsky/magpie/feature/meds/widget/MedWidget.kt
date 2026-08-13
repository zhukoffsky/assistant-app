package com.zhukoffsky.magpie.feature.meds.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.zhukoffsky.magpie.MagpieApp
import com.zhukoffsky.magpie.MainActivity
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.data.db.IntakeStatus
import com.zhukoffsky.magpie.core.settings.forSelectedLanguage
import com.zhukoffsky.magpie.core.ui.theme.MagpieGlanceColors
import com.zhukoffsky.magpie.feature.meds.domain.TodayDose
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

/**
 * Сегодняшний приём лекарства: название, доза и время.
 *
 * **Доза — плитка, залитая акцентом, пока приём не отмечен.** Это то же
 * правило, что и во всём приложении: оранжевый несёт действие, а
 * невыпитая таблетка — это и есть невыполненное действие. Заодно на виджет
 * становится достаточно взглянуть, не читая: горит оранжевым — не принято,
 * погасло — принято. Гореть после отметки незачем: сделанное не требует
 * внимания, а виджет висит на экране весь день.
 *
 * Кнопок нет намеренно. Отметить приём можно из уведомления и с экрана, а
 * промахнуться по «Принял» на домашнем экране — значит соврать истории,
 * которую потом нечем поправить, кроме отметки задним числом.
 */
class MedWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as MagpieApp).container.medRepository

        // Первое значение — до `provideContent`: Glance публикует первый же
        // скомпонованный кадр, и пустое `initial` уехало бы в лаунчер раньше,
        // чем ответит Room. Урок виджета покупок.
        val initial = repository.observeToday().first()

        val strings = context.forSelectedLanguage()
        val locale = strings.resources.configuration.locales[0]

        provideContent {
            val today by repository.observeToday().collectAsState(initial = initial)

            GlanceTheme(colors = MagpieGlanceColors) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        // Ни стекла, ни градиента: Glance умеет только плоскую
                        // заливку со скруглением. Поверхность на тон выше
                        // фона — так виджет читается карточкой, а не дырой.
                        .background(GlanceTheme.colors.background)
                        .cornerRadius(28.dp)
                        .padding(12.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val dose = today
                    if (dose == null) {
                        Text(
                            text = strings.getString(R.string.widget_meds_empty),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                        )
                    } else {
                        DoseContent(
                            dose = dose,
                            amount = dose.doseMg.toString(),
                            unit = strings.getString(R.string.med_unit_mg),
                            time = dose.course.timeOfDay.format(
                                DateTimeFormatter.ofPattern("HH:mm", locale),
                            ),
                            status = strings.getString(dose.status.labelRes),
                        )
                    }
                }
            }
        }
    }
}

class MedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MedWidget()
}

@Composable
private fun DoseContent(
    dose: TodayDose,
    amount: String,
    unit: String,
    time: String,
    status: String,
) {
    val taken = dose.status == IntakeStatus.TAKEN

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DoseTile(amount = amount, unit = unit, taken = taken)

        Spacer(modifier = GlanceModifier.size(14.dp))

        Column {
            Text(
                text = dose.course.name,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                ),
            )
            Text(
                text = time,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.padding(top = 2.dp),
            )
            // Подпись горит в ту же сторону, что и плитка, а не наоборот:
            // два акцента об одном — это одно сообщение, а разнонаправленные
            // читались бы как два разных состояния сразу.
            Text(
                text = status,
                style = TextStyle(
                    color = if (taken) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.primary,
                    fontWeight = if (taken) FontWeight.Normal else FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                modifier = GlanceModifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Доза крупной плиткой.
 *
 * Заливка акцентом — у той, которую ещё не приняли: у неё же и текст
 * выворачивается на `onPrimary`. Принятая уходит на спокойный контейнер, но
 * форму и размер сохраняет — иначе виджет прыгал бы при отметке.
 */
@Composable
private fun DoseTile(amount: String, unit: String, taken: Boolean) {
    Box(
        modifier = GlanceModifier
            /*
             * 72dp при радиусе 28dp — именно скруглённый квадрат.
             *
             * Радиус тот же, что у границы виджета: форма читается
             * продолжением его силуэта. Сторона при этом обязана быть заметно
             * больше двух радиусов, иначе квадрат вырождается в круг — на
             * 64dp ровно это и вышло.
             */
            .size(72.dp)
            // Принятая — на контейнере: в схеме `surfaceVariant` равен фону,
            // и плитка на нём просто исчезла бы, а `surfaceContainer*` Glance
            // не отдаёт вовсе.
            .background(
                if (taken) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.primary,
            )
            .cornerRadius(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = amount,
                style = TextStyle(
                    color = if (taken) GlanceTheme.colors.onSurface else GlanceTheme.colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
            )
            Text(
                text = unit,
                style = TextStyle(
                    color = if (taken) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onPrimary,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

/** Название состояния приёма живёт в ресурсах: виджет двуязычный. */
private val IntakeStatus.labelRes: Int
    get() = when (this) {
        IntakeStatus.PENDING -> R.string.med_status_pending
        IntakeStatus.TAKEN -> R.string.med_status_taken
        IntakeStatus.SKIPPED -> R.string.med_status_skipped
        IntakeStatus.SNOOZED -> R.string.med_status_snoozed
    }
