package com.zhukoffsky.magpie.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Радиусы крупнее материаловых по умолчанию: на этом и держится «органика».
 * Настоящий сквиркл потребовал бы своей Shape с безье по контуру — на глаз
 * при таких радиусах разница неразличима, а стоимость отрисовки заметная.
 */
object MagpieRadius {
    /** Чипы, чекбокс. */
    val sm = 14.dp

    /** Строка списка, поле ввода. */
    val md = 22.dp

    /** Карточка, панель, нижняя навигация. */
    val lg = 30.dp

    /** Модальная карточка диктовки. */
    val xl = 38.dp
}

val MagpieShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(MagpieRadius.sm),
    medium = RoundedCornerShape(MagpieRadius.md),
    large = RoundedCornerShape(MagpieRadius.lg),
    extraLarge = RoundedCornerShape(MagpieRadius.xl),
)
