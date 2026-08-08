package com.zhukoffsky.magpie.feature.reminders.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.PlaceholderScreen

@Composable
fun RemindersScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.nav_reminders),
        description = stringResource(R.string.placeholder_reminders),
    )
}
