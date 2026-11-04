package com.zhukoffsky.magpie.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.zhukoffsky.magpie.core.speech.OnDeviceTranscriber
import com.zhukoffsky.magpie.core.util.MagpieLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Зонд распознавания на устройстве — замер без человека у микрофона.
 *
 * ```
 * adb shell am broadcast -a com.zhukoffsky.magpie.debug.SPEECH_PROBE \
 *     -n com.zhukoffsky.magpie/.debug.SpeechProbeReceiver \
 *     --es lang ru-RU [--es file probe.m4a] [--ez download true] [--es speedup 2]
 * ```
 *
 * Пишет в лог (тег Magpie, префикс `probe:`) поддержку языка и, если указан
 * файл из `cacheDir`, его расшифровку тем же `OnDeviceTranscriber`, что и
 * приложение.
 */
class SpeechProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            MagpieLog.w("probe: needs Android 13")
            return
        }
        val lang = intent.getStringExtra("lang") ?: "ru-RU"
        val fileName = intent.getStringExtra("file")
        val download = intent.getBooleanExtra("download", false)
        val speedup = intent.getStringExtra("speedup")?.toDoubleOrNull() ?: 1.0
        val app = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val transcriber = OnDeviceTranscriber(app, speedup)
                MagpieLog.i("probe: available=${SpeechRecognizer.isOnDeviceRecognitionAvailable(app)}")
                val support = transcriber.support(lang)
                MagpieLog.i(
                    "probe: lang=$lang installed=${support?.installedOnDeviceLanguages} " +
                        "pending=${support?.pendingOnDeviceLanguages} " +
                        "supported=${support?.supportedOnDeviceLanguages?.size} online=${support?.onlineLanguages?.size}",
                )

                if (download) {
                    withContext(Dispatchers.Main) {
                        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
                        recognizer.triggerModelDownload(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang),
                        )
                        recognizer.destroy()
                    }
                    MagpieLog.i("probe: model download requested for $lang")
                }

                if (fileName != null) {
                    val bytes = File(app.cacheDir, fileName).readBytes()
                    val started = System.currentTimeMillis()
                    val text = transcriber.transcribe(bytes, lang)
                    MagpieLog.i("probe: ${System.currentTimeMillis() - started} ms -> «$text»")
                }
            } catch (e: Exception) {
                MagpieLog.w("probe: failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
