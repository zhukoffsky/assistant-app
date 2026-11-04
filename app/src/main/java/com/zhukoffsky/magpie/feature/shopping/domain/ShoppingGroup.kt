package com.zhukoffsky.magpie.feature.shopping.domain

/** Отдел списка; `category = null` — уже купленное. */
data class ShoppingGroup(val category: ShoppingCategory?, val items: List<ShoppingItem>)

/**
 * Список, разложенный по отделам в порядке обхода магазина. Общий для экрана
 * и виджета: разойдись они, на домашнем экране был бы другой список.
 *
 * Порядок внутри отдела — тот же, что и в плоском списке. Пустые отделы
 * не показываются, «Прочее» оказывается последним само — по порядку
 * объявления в перечислении.
 *
 * Купленное — не в своём отделе, а отдельной группой в самом низу, как и в
 * плоском списке. До 4 ноября оно оставалось в отделе, и до конца похода
 * нужное перемежалось зачёркнутым.
 */
fun List<ShoppingItem>.groupedForShop(): List<ShoppingGroup> {
    val (bought, needed) = partition { it.isChecked }
    val aisles = ShoppingCategory.entries.mapNotNull { category ->
        needed.filter { it.category == category }
            .takeIf { it.isNotEmpty() }
            ?.let { ShoppingGroup(category, it) }
    }
    return if (bought.isEmpty()) aisles else aisles + ShoppingGroup(category = null, items = bought)
}
