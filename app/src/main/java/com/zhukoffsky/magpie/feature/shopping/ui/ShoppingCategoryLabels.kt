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
        ShoppingCategory.READY_MEALS -> R.string.category_ready_meals
        ShoppingCategory.DAIRY -> R.string.category_dairy
        ShoppingCategory.CHEESE -> R.string.category_cheese
        ShoppingCategory.MEAT -> R.string.category_meat
        ShoppingCategory.FISH -> R.string.category_fish
        ShoppingCategory.DELI -> R.string.category_deli
        ShoppingCategory.GROCERY -> R.string.category_grocery
        ShoppingCategory.CANNED -> R.string.category_canned
        ShoppingCategory.CONDIMENTS -> R.string.category_condiments
        ShoppingCategory.BREAKFAST -> R.string.category_breakfast
        ShoppingCategory.SWEETS -> R.string.category_sweets
        ShoppingCategory.SNACKS -> R.string.category_snacks
        ShoppingCategory.TEA_COFFEE -> R.string.category_tea_coffee
        ShoppingCategory.DRINKS -> R.string.category_drinks
        ShoppingCategory.ALCOHOL -> R.string.category_alcohol
        ShoppingCategory.FROZEN -> R.string.category_frozen
        ShoppingCategory.HOUSEHOLD -> R.string.category_household
        ShoppingCategory.HYGIENE -> R.string.category_hygiene
        ShoppingCategory.HEALTH -> R.string.category_health
        ShoppingCategory.KIDS -> R.string.category_kids
        ShoppingCategory.PETS -> R.string.category_pets
        ShoppingCategory.OTHER -> R.string.category_other
    }
