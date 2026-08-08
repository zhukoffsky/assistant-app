package com.zhukoffsky.magpie.core.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.zhukoffsky.magpie.R

/** Разделы нижней навигации. Порядок в enum задаёт порядок вкладок. */
enum class MagpieDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Shopping("shopping", R.string.nav_shopping, Icons.Default.ShoppingCart),
    Reminders("reminders", R.string.nav_reminders, Icons.Default.Notifications),
    Meds("meds", R.string.nav_meds, Icons.Default.Favorite),
    ;

    companion object {
        val START = Shopping
    }
}
