package com.zhukoffsky.magpie.feature.shopping.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.PlaceholderScreen

@Composable
fun ShoppingScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_shopping),
        description = stringResource(R.string.placeholder_shopping),
    )
}
