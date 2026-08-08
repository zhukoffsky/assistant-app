package com.zhukoffsky.magpie.feature.shopping.domain

/**
 * Элемент списка покупок в том виде, в котором его знает UI.
 *
 * Отдельно от сущности БД: полей синхронизации и меток времени экрану знать
 * не нужно, а тащить их в UI — значит завязать разметку на схему таблицы.
 */
data class ShoppingItem(
    val id: Long,
    val title: String,
    val isChecked: Boolean,
)
