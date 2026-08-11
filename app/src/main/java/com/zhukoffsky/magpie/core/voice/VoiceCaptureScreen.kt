package com.zhukoffsky.magpie.core.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.GlassSurface
import com.zhukoffsky.magpie.core.ui.blurBehindWindow
import com.zhukoffsky.magpie.core.ui.staggeredEntrance
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.feature.reminders.ui.dueLabel

/**
 * Оверлей поверх прозрачной активности: пока идёт распознавание, на экране
 * системный диалог, и своего интерфейса быть не должно.
 */
@Composable
fun VoiceCaptureScreen(
    state: VoiceCaptureUiState,
    target: VoiceTarget,
    onItemChange: (Int, String) -> Unit,
    onItemRemove: (Int) -> Unit,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is VoiceCaptureUiState.Listening, VoiceCaptureUiState.Done -> Unit

        is VoiceCaptureUiState.Parsing -> BottomCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    // Ожидание общее для обеих целей, а текст — нет: у
                    // покупок никакого времени не разбирается.
                    text = stringResource(
                        when (target) {
                            VoiceTarget.SHOPPING -> R.string.voice_parsing_shopping
                            VoiceTarget.REMINDER -> R.string.voice_parsing
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MagpieTheme.colors.ink,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        is VoiceCaptureUiState.ConfirmingItems -> BottomCard {
            CardHeader(stringResource(R.string.voice_confirm_shopping_title))
            Column(
                // Ограничение по высоте, а не свободный рост: длинный список
                // иначе выдавит кнопки за нижний край экрана.
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.items.forEachIndexed { index, item ->
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MagpieRadius.sm),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardTextField(
                                value = item,
                                onValueChange = { onItemChange(index, it) },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onItemRemove(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.voice_remove_item),
                                    tint = MagpieTheme.colors.ink3,
                                )
                            }
                        }
                    }
                }
            }
            Buttons(onCancel = onCancel, onConfirm = onConfirm)
        }

        is VoiceCaptureUiState.ConfirmingReminder -> BottomCard {
            CardHeader(stringResource(R.string.voice_confirm_reminder_title))
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieRadius.sm),
            ) {
                CardTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
            Text(
                text = dueLabel(state.dueAt, state.repeat),
                style = MaterialTheme.typography.bodySmall,
                color = MagpieTheme.colors.ink2,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )
            Buttons(onCancel = onCancel, onConfirm = onConfirm)
        }

        is VoiceCaptureUiState.Failed -> BottomCard {
            val messageRes = when (state.reason) {
                VoiceFailure.NO_RECOGNIZER -> R.string.voice_error_no_recognizer
                VoiceFailure.NOTHING_RECOGNIZED -> R.string.voice_error_nothing_recognized
            }
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MagpieTheme.colors.ink,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.voice_close)) }
                if (state.reason != VoiceFailure.NO_RECOGNIZER) {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(MagpieRadius.sm),
                    ) {
                        Text(stringResource(R.string.voice_retry))
                    }
                }
            }
        }
    }
}

/**
 * Радиус размытия задника. Подбирается на глаз: мельче — грязь от плавающей
 * навигации ещё читается, крупнее — фон превращается в однородное пятно и
 * теряется ощущение, что за карточкой тот самый список.
 */
private val BackdropBlur = 24.dp

/**
 * Карточка внизу экрана поверх задника.
 *
 * Снизу, а не по центру: активность прозрачная, за ней виден тот самый
 * список, куда попадёт запись, и карточка не должна его закрывать целиком.
 *
 * Задник обрабатывается двумя способами, и выбор делает не версия Android, а
 * [blurBehindWindow]: система гасит размытие при энергосбережении и на
 * неподдерживающих устройствах, поэтому спрашивать надо о текущем состоянии,
 * а не о API-уровне.
 */
@Composable
private fun BottomCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(MagpieRadius.xl)
    val blurred = blurBehindWindow(BackdropBlur)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Поверх размытия притемнение нужно только чтобы увести задник на
            // задний план; без размытия оно единственное, что отделяет
            // карточку от чужого экрана, — отсюда разница почти в три раза.
            .background(Color.Black.copy(alpha = if (blurred) 0.16f else 0.45f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        /*
         * Карточка непрозрачная ВСЕГДА, размытие на это не влияет.
         *
         * Стекло здесь пробовали и вернули обратно. Расчёт был на то, что
         * размытие снимает контрастные края и сквозь заливку в 13% белого
         * ничего мешающего не останется. На Pixel 8 осталось: замер по
         * скриншоту дал внутри одной карточки #37302D там, где за ней пустой
         * фон, и #684E43 там, где за ней плавающая навигация. Полоса
         * навигации отчётливо читалась прямо под кнопками «Отмена» и
         * «Сохранить».
         *
         * Причина в том, что размывается ЧУЖОЙ экран, а не мягкий фон
         * приложения: у навигации собственная заливка и своя яркость, и
         * размытие её приглушает, но не убирает.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Карточка прижата к низу, поэтому жест-полоса ложилась прямо
                // на неё, срезая нижнее скругление и кнопку «Сохранить».
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(14.dp)
                .staggeredEntrance(index = 0)
                .background(MagpieTheme.colors.background, shape)
                .border(BorderStroke(1.dp, MagpieTheme.colors.glassBorder), shape),
        ) {
            Column(modifier = Modifier.padding(22.dp)) { content() }
        }
    }
}

/** Заголовок карточки с микрофоном — видно, откуда взялась запись. */
@Composable
private fun CardHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(MagpieRadius.sm),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MagpieTheme.colors.ink,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Поле без своей рамки: её рисует стекло вокруг. */
@Composable
private fun CardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(vertical = 14.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MagpieTheme.colors.ink),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun Buttons(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.voice_cancel)) }
        Button(
            onClick = onConfirm,
            modifier = Modifier.padding(start = 8.dp),
            shape = RoundedCornerShape(MagpieRadius.sm),
        ) {
            Text(stringResource(R.string.voice_save))
        }
    }
}
