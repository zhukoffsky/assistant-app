package com.zhukoffsky.magpie

import android.Manifest
import android.annotation.SuppressLint
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
import com.zhukoffsky.magpie.core.ui.MagpieRoot
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат не влияет на UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MagpieRoot {
                // Запуск активностей и системных экранов — обязанность точки
                // входа, а не composable.
                MagpieAppScaffold(
                    onVoiceCapture = { target ->
                        startActivity(VoiceCaptureActivity.intent(this, target))
                    },
                    onOpenFix = ::openFix,
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

    /**
     * `BatteryLife` подавлен на всей функции, а не на ветке: аннотация на
     * ветке `when` lint'ом не читается.
     *
     * Возражает он по политике Google Play, а приложение там не публикуется —
     * то же рассуждение, что и с `USE_EXACT_ALARM`.
     */
    @SuppressLint("BatteryLife")
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

            /*
             * Адресный диалог, а не общий список приложений.
             *
             * `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` открывает перечень
             * всех приложений: себя надо найти по алфавиту, а переключатель
             * там соседствует с «ограничить фоновую активность», хотя это
             * разное. В белый список Doze — то, что читает проверка, — кладёт
             * только «Без ограничений»; выставив соседний переключатель,
             * человек видит, что проверка всё равно горит, и не понимает
             * почему.
             *
             * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` с `package:`
             * показывает один системный вопрос и ставит ровно нужный флаг.
             */
            DiagnosticFix.BATTERY_OPTIMIZATION -> startSettings(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri()),
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
