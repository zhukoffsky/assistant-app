package com.zhukoffsky.magpie.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Палитра «тёплое стекло».
 *
 * Основа почти нейтральная, но с оранжевым подтоном, и ровно один акцент.
 * Акцент несёт только действие — кнопку, активную вкладку, принятую дозу;
 * всё остальное держится на прозрачности и тени, а не на цвете.
 */

// ── Светлая ───────────────────────────────────────────────────────────────
internal val LightBg = Color(0xFFFFF7F2)
internal val LightInk = Color(0xFF2A1D18)
internal val LightInk2 = Color(0xFF7A655C)
internal val LightInk3 = Color(0xFFA9948A)
internal val LightAccent = Color(0xFFFF6B35)
internal val LightAccentPressed = Color(0xFFE4551F)
internal val LightOnAccent = Color(0xFFFFFFFF)
internal val LightOk = Color(0xFF1E9E6A)
internal val LightWarn = Color(0xFFC7791B)

// ── Тёмная ────────────────────────────────────────────────────────────────
internal val DarkBg = Color(0xFF151110)
internal val DarkInk = Color(0xFFFCF5F0)
internal val DarkInk2 = Color(0xFFC6B3A8)
internal val DarkInk3 = Color(0xFF9A8478)
internal val DarkAccent = Color(0xFFFF8551)
internal val DarkAccentPressed = Color(0xFFFF6B35)
internal val DarkOnAccent = Color(0xFF26150D)
internal val DarkOk = Color(0xFF3DD598)
internal val DarkWarn = Color(0xFFE7A74A)

/**
 * Токены, которым нет места в `ColorScheme` Material 3.
 *
 * Стекло, пятна градиента и третий уровень текста — не части материаловой
 * схемы, но без них дизайн рассыпается. Держим их рядом со схемой, а не
 * растаскиваем по экранам.
 */
@Immutable
data class MagpieColors(
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ok: Color,
    val warn: Color,
    /** Заливка стекла, когда размытие доступно (API 31+). */
    val glass: Color,
    /** Заливка стекла, когда размытия нет: плотнее, чтобы текст остался читаемым. */
    val glassOpaque: Color,
    val glassBorder: Color,
    /** Три пятна органического фона, снизу вверх по слоям. */
    val meshTopLeft: Color,
    val meshTopRight: Color,
    val meshBottom: Color,
    val background: Color,
)

internal val LightMagpieColors = MagpieColors(
    ink = LightInk,
    ink2 = LightInk2,
    ink3 = LightInk3,
    ok = LightOk,
    warn = LightWarn,
    glass = Color.White.copy(alpha = 0.62f),
    glassOpaque = Color.White.copy(alpha = 0.86f),
    glassBorder = Color.White.copy(alpha = 0.85f),
    meshTopLeft = Color(0xFFFFC9A8).copy(alpha = 0.70f),
    meshTopRight = Color(0xFFFFB0AC).copy(alpha = 0.62f),
    meshBottom = Color(0xFFFFE1AE).copy(alpha = 0.75f),
    background = LightBg,
)

internal val DarkMagpieColors = MagpieColors(
    ink = DarkInk,
    ink2 = DarkInk2,
    ink3 = DarkInk3,
    ok = DarkOk,
    warn = DarkWarn,
    glass = Color.White.copy(alpha = 0.085f),
    glassOpaque = Color.White.copy(alpha = 0.13f),
    glassBorder = Color.White.copy(alpha = 0.15f),
    // Заметно слабее, чем в HTML-макете: там пятна лежат на маленькой карточке
    // предпросмотра, а на экране телефона та же альфа даёт сплошную ржавчину
    // вместо свечения по углам.
    meshTopLeft = Color(0xFFFF6B35).copy(alpha = 0.15f),
    meshTopRight = Color(0xFFB23A1A).copy(alpha = 0.14f),
    meshBottom = Color(0xFF7A2F12).copy(alpha = 0.20f),
    background = DarkBg,
)

/**
 * Ошибиться здесь дороже, чем кажется: без темы обращение к `MagpieColors`
 * молча вернуло бы светлые токены поверх тёмного фона.
 */
val LocalMagpieColors = staticCompositionLocalOf<MagpieColors> {
    error("MagpieColors недоступны: обёртка MagpieTheme не найдена")
}
