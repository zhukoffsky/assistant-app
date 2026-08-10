package com.zhukoffsky.magpie.core.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhukoffsky.magpie.R

/**
 * Оверлей поверх прозрачной активности: пока идёт распознавание, на экране
 * системный диалог, и своего интерфейса быть не должно.
 */
@Composable
fun VoiceCaptureScreen(
    state: VoiceCaptureUiState,
    onItemChange: (Int, String) -> Unit,
    onItemRemove: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is VoiceCaptureUiState.Listening, VoiceCaptureUiState.Done -> Unit

        is VoiceCaptureUiState.Confirming -> CenteredCard {
            ConfirmContent(
                items = state.items,
                onItemChange = onItemChange,
                onItemRemove = onItemRemove,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }

        is VoiceCaptureUiState.Failed -> CenteredCard {
            FailureContent(reason = state.reason, onRetry = onRetry, onCancel = onCancel)
        }
    }
}

@Composable
private fun CenteredCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) { content() }
        }
    }
}

@Composable
private fun ConfirmContent(
    items: List<String>,
    onItemChange: (Int, String) -> Unit,
    onItemRemove: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = stringResource(R.string.voice_confirm_shopping_title),
        style = MaterialTheme.typography.titleMedium,
    )

    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item,
                    onValueChange = { onItemChange(index, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { onItemRemove(index) }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.voice_remove_item),
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.voice_cancel)) }
        TextButton(onClick = onConfirm) { Text(stringResource(R.string.voice_save)) }
    }
}

@Composable
private fun FailureContent(
    reason: VoiceFailure,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val messageRes = when (reason) {
        VoiceFailure.NO_RECOGNIZER -> R.string.voice_error_no_recognizer
        VoiceFailure.NOTHING_RECOGNIZED -> R.string.voice_error_nothing_recognized
    }

    Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.voice_close)) }
        if (reason != VoiceFailure.NO_RECOGNIZER) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.voice_retry)) }
        }
    }
}
