package com.zhukoffsky.magpie

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.zhukoffsky.magpie.core.diagnostics.DiagnosticFix
import com.zhukoffsky.magpie.core.ui.MagpieAppScaffold
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат не влияет на UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MagpieTheme {
                // Запуск активностей и системных экранов — обязанность точки
                // входа, а не composable.
                MagpieAppScaffold(
                    onVoiceCapture = { target ->
                        startActivity(VoiceCaptureActivity.intent(this, target))
                    },
                    onOpenFix = ::openFix,
                    onShareText = ::shareText,
                )
            }
        }

        if (savedInstanceState == null) requestNotificationPermission()
    }

    /**
     * С Android 13 без этого разрешения уведомления просто не показываются.
     * Спрашиваем при первом запуске; дальше — через экран самодиагностики.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Экспорт истории: отдаём текст системе, дальше пользователь решает сам. */
    private fun shareText(text: String) {
        if (text.isBlank()) return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.med_export)))
    }

    private fun openFix(fix: DiagnosticFix) {
        when (fix) {
            DiagnosticFix.NOTIFICATION_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Если пользователь отказал дважды, система запрос больше
                    // не показывает — тогда остаются только настройки.
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openFix(DiagnosticFix.NOTIFICATION_SETTINGS)
                }
            }

            DiagnosticFix.NOTIFICATION_SETTINGS -> startSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )

            DiagnosticFix.EXACT_ALARMS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startSettings(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri()),
                    )
                }
            }

            DiagnosticFix.BATTERY_OPTIMIZATION -> startSettings(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )

            DiagnosticFix.APP_SETTINGS -> startSettings(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri()),
            )
        }
    }

    /**
     * Часть системных экранов есть не на всех прошивках. Падать из-за этого
     * нельзя — откатываемся на карточку приложения в настройках.
     */
    private fun startSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri()))
            }
        }
    }

    private fun appUri(): Uri = Uri.fromParts("package", packageName, null)
}
