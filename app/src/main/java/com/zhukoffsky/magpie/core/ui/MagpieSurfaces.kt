package com.zhukoffsky.magpie.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.zhukoffsky.magpie.core.ui.theme.MagpieColors
import com.zhukoffsky.magpie.core.ui.theme.MagpieRadius
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme

/*
 * Два слоя дизайна: органический градиентный фон и «стекло» поверх него.
 *
 * ВАЖНО про стекло. В Compose нет backdrop-blur — размытия того, что лежит
 * ПОД элементом. `Modifier.blur` размывает собственное содержимое элемента
 * (и работает только с API 31), то есть для матовой панели не годится вовсе:
 * им можно размыть текст на карточке, но не фон за ней.
 *
 * Поэтому стекло здесь — полупрозрачная заливка с тонкой светлой рамкой
 * поверх заведомо мягкого градиента. Фон под карточкой и так без резких
 * границ, так что глаз читает это как матовое стекло. Это не компромисс
 * ради старых версий: на любом Android другого способа нет.
 */

/** Органический фон: базовый цвет и три радиальных пятна поверх него. */
fun Modifier.magpieMesh(colors: MagpieColors, phase: Float = 0f): Modifier = drawBehind {
    drawRect(colors.background)

    val w = size.width
    val h = size.height
    val far = maxOf(w, h)

    // Пятна смещаются на считаные проценты — движение должно ощущаться
    // как дыхание, а не как анимация.
    val drift = phase * 0.02f

    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.meshTopLeft, Color.Transparent),
            center = Offset(w * 0.10f, h * (0.06f + drift)),
            radius = far * 0.62f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.meshTopRight, Color.Transparent),
            center = Offset(w * 0.92f, h * (0.10f - drift)),
            radius = far * 0.58f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.meshBottom, Color.Transparent),
            center = Offset(w * 0.52f, h * (1.04f + drift)),
            radius = far * 0.76f,
        ),
    )
}

/**
 * Фон экрана целиком.
 *
 * @param animated медленное «дыхание» пятен. Гасить его, когда экран не
 * виден, обязательно: бесконечная анимация не даёт композиции заснуть и
 * ощутимо тратит батарею — а это ровно то, ради чего в приложении
 * выбирались точные будильники вместо периодических пробуждений.
 */
@Composable
fun MagpieBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MagpieTheme.colors
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "mesh")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16_000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "meshPhase",
        )
        value
    } else {
        0f
    }

    Box(modifier = modifier.magpieMesh(colors, phase), content = content)
}

/** Матовая панель: полупрозрачная заливка плюс светлая рамка в один пиксель. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MagpieRadius.lg),
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MagpieTheme.colors
    Box(
        modifier = modifier
            .background(
                color = if (strong) colors.glassOpaque else colors.glass,
                shape = shape,
            )
            .border(BorderStroke(1.dp, colors.glassBorder), shape),
        content = content,
    )
}

/** Заполняет доступное место — удобно для фона экрана. */
@Composable
fun MagpieScreenBackground(
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) = MagpieBackground(
    modifier = Modifier.fillMaxSize(),
    animated = animated,
    content = content,
)
