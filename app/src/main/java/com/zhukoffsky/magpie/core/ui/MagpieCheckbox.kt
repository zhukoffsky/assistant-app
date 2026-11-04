package com.zhukoffsky.magpie.core.ui

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme

/**
 * Галка «сделано» для списков покупок и напоминаний.
 *
 * **Отмеченная — приглушённая, а не залитая акцентом.** Раньше купленная
 * позиция горела оранжевым квадратом и была самым ярким местом списка, хотя
 * внимания она больше не требует. Это то же правило, что у виджета таблеток:
 * горит то, что ещё ждёт руки, а сделанное уходит в фон вместе с
 * зачёркнутым текстом.
 */
@Composable
fun MagpieCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MagpieTheme.colors
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = CheckboxDefaults.colors(
            checkedColor = colors.ink3,
            checkmarkColor = colors.background,
            uncheckedColor = colors.ink3,
        ),
    )
}
