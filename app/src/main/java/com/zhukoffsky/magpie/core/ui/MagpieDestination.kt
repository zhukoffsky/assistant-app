package com.zhukoffsky.magpie.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.zhukoffsky.magpie.R

/**
 * Разделы нижней навигации. Порядок в enum задаёт порядок вкладок.
 *
 * Иконки — свои ресурсы, а не `Icons.Default.*`: артефакт с иконками
 * Material закончился на 1.7.8, его перестали развивать. Формы те же, из
 * официального репозитория, лежат в `res/drawable`.
 */
enum class MagpieDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
) {
    Shopping("shopping", R.string.nav_shopping, R.drawable.ic_shopping_cart),
    Reminders("reminders", R.string.nav_reminders, R.drawable.ic_notifications),
    Meds("meds", R.string.nav_meds, R.drawable.ic_favorite),
    Settings("settings", R.string.nav_settings, R.drawable.ic_settings),
    ;

    companion object {
        val START = Shopping
    }
}
