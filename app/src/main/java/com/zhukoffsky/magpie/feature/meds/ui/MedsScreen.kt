package com.zhukoffsky.magpie.feature.meds.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.PlaceholderScreen

@Composable
fun MedsScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_meds),
        description = stringResource(R.string.placeholder_meds),
    )
}
