package com.zhukoffsky.magpie.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticCheck
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticFix

@Composable
fun SettingsScreen(
    onOpenFix: (DiagnosticFix) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Пользователь уходит в системные настройки и возвращается — состояние
    // надо перечитать, иначе экран будет показывать вчерашнюю правду.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.diag_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = stringResource(R.string.diag_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )

        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = state.checks, key = { it.id }) { check ->
                CheckRow(check = check, onOpenFix = onOpenFix)
            }
        }

        HorizontalDivider()

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = viewModel::onTestNotification,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diag_test_button))
            }
            if (state.testScheduled) {
                Text(
                    text = stringResource(R.string.diag_test_scheduled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CheckRow(check: DiagnosticCheck, onOpenFix: (DiagnosticFix) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (check.isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (check.isOk) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(check.titleRes), style = MaterialTheme.typography.bodyLarge)

            // Объяснение показывается только когда есть проблема: список
            // из шести абзацев «всё хорошо» читать невозможно.
            if (!check.isOk) {
                Text(
                    text = stringResource(check.problemRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val fix = check.fix
        if (!check.isOk && fix != null) {
            TextButton(onClick = { onOpenFix(fix) }) {
                Text(stringResource(R.string.diag_fix))
            }
        }
    }
}
