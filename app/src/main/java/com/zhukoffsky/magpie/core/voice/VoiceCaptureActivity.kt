package com.zhukoffsky.magpie.core.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import java.util.Locale

/**
 * Точка входа голосового ввода: виджет, плитка или кнопка на экране ведут
 * сюда, активность немедленно открывает системный диалог распознавания.
 *
 * Разрешение `RECORD_AUDIO` здесь не нужно: запись ведёт приложение-
 * распознаватель в своём процессе и со своими разрешениями. Оно
 * понадобилось бы при переходе на `SpeechRecognizer` с собственным
 * интерфейсом.
 *
 * Активность прозрачная и живёт в отдельной задаче — она не должна
 * появляться в списке недавних и подмешиваться в стек главного экрана.
 */
class VoiceCaptureActivity : ComponentActivity() {

    private val target: VoiceTarget by lazy { targetOf(intent) }

    private val viewModel: VoiceCaptureViewModel by viewModels {
        VoiceCaptureViewModel.factory(target)
    }

    private val recognition = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        viewModel.onRecognitionResult(spoken.takeIf { result.resultCode == RESULT_OK })
    }

    /**
     * Активность живёт в отдельной задаче с `singleTask`, поэтому вторая
     * точка входа может попасть в уже открытый экран. Если цель другая —
     * перезапускаемся: цикла не будет, у новой копии цель совпадёт.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (targetOf(intent) == target) return

        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MagpieTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(state) {
                    if (state == VoiceCaptureUiState.Done) finish()
                    if (viewModel.shouldStartRecognition()) startRecognition()
                }

                VoiceCaptureScreen(
                    state = state,
                    onItemChange = viewModel::onItemChange,
                    onItemRemove = viewModel::onItemRemove,
                    onTitleChange = viewModel::onTitleChange,
                    onConfirm = viewModel::onConfirm,
                    onCancel = ::finish,
                    onRetry = viewModel::onRetry,
                )
            }
        }
    }

    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            val promptRes = when (target) {
                VoiceTarget.SHOPPING -> R.string.voice_prompt_shopping
                VoiceTarget.REMINDER -> R.string.voice_prompt_reminder
            }
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(promptRes))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognition.launch(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.onRecognizerMissing()
        }
    }

    companion object {
        fun intent(context: Context, target: VoiceTarget): Intent =
            Intent(context, VoiceCaptureActivity::class.java)
                .setAction(target.action)
                .putExtra(VoiceTarget.EXTRA_TARGET, target.name)

        private fun targetOf(intent: Intent): VoiceTarget =
            VoiceTarget.fromAction(intent.action)
                ?: VoiceTarget.fromName(intent.getStringExtra(VoiceTarget.EXTRA_TARGET))
                ?: VoiceTarget.SHOPPING
    }
}
