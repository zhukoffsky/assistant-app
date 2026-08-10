package com.zhukoffsky.magpie.feature.settings.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticCheck
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticFix
import com.zhukoffsky.magpie.core.sync.SyncSettings
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenFix: (DiagnosticFix) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncSettings by viewModel.syncSettings.collectAsStateWithLifecycle()
    val consentRequest by viewModel.consentRequest.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()

    // Пользователь уходит в системные настройки и возвращается — состояние
    // надо перечитать, иначе экран будет показывать вчерашнюю правду.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentHandled(granted = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(consentRequest) {
        consentRequest?.let { pendingIntent ->
            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GoogleSyncCard(
            settings = syncSettings,
            onConnect = viewModel::onConnectGoogle,
            onSyncNow = viewModel::onSyncNow,
            onDisconnect = viewModel::onDisconnectGoogle,
        )

        HorizontalDivider()

        ApiKeyCard(
            hasApiKey = hasApiKey,
            onSave = viewModel::onApiKeyEntered,
            onClear = viewModel::onApiKeyCleared,
        )

        HorizontalDivider()

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
private fun GoogleSyncCard(
    settings: SyncSettings,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.sync_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.sync_one_way_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (!settings.isEnabled) {
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.sync_connect))
            }
            return@Column
        }

        Text(
            text = settings.lastSyncAt
                ?.let { stringResource(R.string.sync_last, formatter.format(it)) }
                ?: stringResource(R.string.sync_never),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        settings.lastError?.let { error ->
            Text(
                text = stringResource(R.string.sync_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSyncNow) { Text(stringResource(R.string.sync_now)) }
            TextButton(onClick = onDisconnect) { Text(stringResource(R.string.sync_disconnect)) }
        }
    }
}

@Composable
private fun ApiKeyCard(
    hasApiKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.llm_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.llm_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (hasApiKey) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.llm_key_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.llm_key_clear)) }
            }
            return@Column
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.llm_key_hint)) },
            singleLine = true,
            // Ключ не показывается даже при вводе и не попадает в
            // автозаполнение и подсказки клавиатуры.
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        TextButton(
            onClick = {
                onSave(draft)
                draft = ""
            },
        ) {
            Text(stringResource(R.string.llm_key_save))
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
