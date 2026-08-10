package com.zhukoffsky.magpie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhukoffsky.magpie.core.ui.MagpieAppScaffold
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MagpieTheme {
                // Запуск активности — обязанность точки входа, а не composable.
                MagpieAppScaffold(
                    onVoiceCapture = { target ->
                        startActivity(VoiceCaptureActivity.intent(this, target))
                    },
                )
            }
        }
    }
}
