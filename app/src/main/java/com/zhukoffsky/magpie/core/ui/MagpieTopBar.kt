package com.zhukoffsky.magpie.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.theme.MagpieMotion
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme

/**
 * Шапка экрана — одна на все вкладки и вложенные экраны.
 *
 * Раньше шапки не было вовсе, а вложенный экран курса возвращался ссылкой
 * «← Все курсы»: стрелка-символ в 13sp, в которую трудно попасть и которая
 * выглядела опечаткой. Теперь «назад» — обычная иконка в 48dp, а заголовок
 * стоит на одном месте на любом экране, и глаз находит его не ища.
 *
 * Фона у шапки нет: под ней тот же градиент, что и под всем экраном. Край,
 * за который уезжает прокрутка, отмечает [fadingTopEdge] у списка ниже.
 */
@Composable
fun MagpieTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = if (onBack == null) 22.dp else 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.action_back),
                    tint = MagpieTheme.colors.ink,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MagpieTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/**
 * Мягкий край прокрутки: содержимое, уходящее под шапку, растворяется.
 *
 * Без него список обрезался ровной линией, и на экране оставалась половина
 * карточки с отрубленным верхом. Включается только когда под краем
 * действительно что-то есть ([scrolled]) — в покое первая строка не должна
 * быть бледнее остальных.
 */
private fun Modifier.fadeTop(scrolled: Boolean, length: Dp): Modifier = this.then(
    Modifier
        // Растворение — это маска по альфе, а её можно наложить только на
        // отрисованный в отдельный слой результат.
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            if (scrolled) {
                val edge = length.toPx()
                drawRect(
                    brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = edge),
                    size = Size(size.width, edge),
                    blendMode = BlendMode.DstIn,
                )
            }
        },
)

@Composable
fun Modifier.fadingTopEdge(scrolled: Boolean): Modifier {
    // Край появляется пружиной, а не скачком: иначе первая же прокрутка
    // на пиксель заметно «щёлкала» верхом списка.
    val length by animateDpAsState(
        targetValue = if (scrolled) 20.dp else 0.dp,
        animationSpec = MagpieMotion.snappy(),
        label = "fadingEdge",
    )
    return fadeTop(scrolled = length > 0.dp, length = length)
}
