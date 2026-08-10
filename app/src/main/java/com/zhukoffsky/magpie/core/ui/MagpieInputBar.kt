package com.zhukoffsky.magpie.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(text = placeholder, color = MagpieTheme.colors.ink3)
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Клавиатура намеренно не скрывается: подряд идущие записи
                // удобнее добавлять не закрывая её.
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                // Поле рисует только текст: фон и рамку даёт стекло вокруг,
                // иначе получилось бы две рамки одна в другой.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MagpieTheme.colors.ink,
                    unfocusedTextColor = MagpieTheme.colors.ink,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            AccentSquareButton(onClick = onSubmit, contentDescription = addContentDescription) {
                Icon(Icons.Default.Add, contentDescription = null)
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
