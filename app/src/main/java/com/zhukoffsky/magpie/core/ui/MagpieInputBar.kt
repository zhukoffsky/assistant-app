package com.zhukoffsky.magpie.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme

/** Единый отступ таблетки: и по краям, и между полем и кнопками. */
private val INSET = 8.dp

/**
 * Строка ввода: поле и обе кнопки живут внутри одной стеклянной таблетки.
 *
 * Кнопки намеренно не вынесены наружу. Снаружи они читаются как три
 * независимых элемента, и глазу приходится решать, к чему относится
 * микрофон; внутри — как одно место ввода с двумя способами заполнить его.
 *
 * Общая для покупок и напоминаний: отличаются только подсказка и то, что
 * происходит по нажатию.
 */
@Composable
fun MagpieInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceInput: () -> Unit,
    placeholder: String,
    addContentDescription: String,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        shape = RoundedCornerShape(MagpieRadius.md),
    ) {
        Row(
            /*
             * Отступ одинаковый со всех сторон. Держится он на том, что
             * высоту таблетки задают кнопки, а не поле: у Material `TextField`
             * своя минимальная высота 56 dp, из-за неё кнопка центрировалась
             * в более высокой строке и сверху с боков зазоры расходились.
             * `BasicTextField` своей высоты не навязывает.
             */
            modifier = Modifier
                .fillMaxWidth()
                .padding(INSET),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(INSET),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
                    .copy(color = MagpieTheme.colors.ink),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Клавиатура намеренно не скрывается: подряд идущие записи
                // удобнее добавлять не закрывая её.
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MagpieTheme.colors.ink3,
                            )
                        }
                        inner()
                    }
                },
            )
            AccentSquareButton(onClick = onSubmit, contentDescription = addContentDescription) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            }
            AccentSquareButton(
                onClick = onVoiceInput,
                contentDescription = stringResource(R.string.voice_input),
            ) {
                Icon(painter = painterResource(R.drawable.ic_mic), contentDescription = null)
            }
        }
    }
}

/** Акцентная кнопка со скруглением — «плюс» и микрофон. */
@Composable
fun AccentSquareButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        // Подпись вешается на саму кнопку, а иконка внутри остаётся немой —
        // иначе TalkBack прочитает её дважды.
        modifier = modifier
            .size(46.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(MagpieRadius.sm),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = { content() },
    )
}
