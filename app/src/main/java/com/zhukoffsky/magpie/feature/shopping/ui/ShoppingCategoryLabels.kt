package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.annotation.StringRes
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.feature.shopping.domain.ShoppingCategory

/**
 * Название отдела живёт в ресурсах: список двуязычный.
 *
 * Лежит здесь, а не в `domain`: перечисление про магазин, а не про Android, и
 * идентификаторам ресурсов в нём не место. Пользуются оба — экран и виджет.
 */
@get:StringRes
val ShoppingCategory.labelRes: Int
    get() = when (this) {
        ShoppingCategory.PRODUCE -> R.string.category_produce
        ShoppingCategory.BAKERY -> R.string.category_bakery
        ShoppingCategory.DAIRY -> R.string.category_dairy
        ShoppingCategory.MEAT -> R.string.category_meat
        ShoppingCategory.GROCERY -> R.string.category_grocery
        ShoppingCategory.FROZEN -> R.string.category_frozen
        ShoppingCategory.DRINKS -> R.string.category_drinks
        ShoppingCategory.HOUSEHOLD -> R.string.category_household
        ShoppingCategory.OTHER -> R.string.category_other
    }
