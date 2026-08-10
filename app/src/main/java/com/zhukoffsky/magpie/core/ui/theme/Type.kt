package com.zhukoffsky.magpie.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Шкала сжата до шести ролей: больше на четырёх экранах не нужно, а каждый
 * лишний размер потом приходится тащить через всю тему.
 *
 * Системный шрифт (на Android это Roboto) уже умеет кириллицу во всех
 * начертаниях — своих файлов не подключаем.
 *
 * Отрицательный трекинг только на крупных ролях: на мелких кеглях кириллица
 * от него слипается заметнее латиницы.
 */
private val Display = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.75).sp,
)

private val Title = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.31).sp,
)

private val Head = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.14).sp,
)

private val Body = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 21.sp,
)

private val Label = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.05.sp,
)

private val Caption = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/**
 * Шесть ролей разложены по слотам Material 3, чтобы готовые компоненты
 * (кнопки, поля, снекбар) подхватили их без ручной простановки стиля.
 */
val MagpieTypography = Typography(
    displaySmall = Display,
    headlineMedium = Display,
    headlineSmall = Title,
    titleLarge = Title,
    titleMedium = Head,
    titleSmall = Head,
    bodyLarge = Body,
    bodyMedium = Body,
    bodySmall = Caption,
    labelLarge = Label,
    labelMedium = Label,
    labelSmall = Caption,
)
