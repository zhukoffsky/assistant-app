package com.zhukoffsky.magpie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhukoffsky.magpie.core.ui.MagpieAppScaffold
import com.zhukoffsky.magpie.core.ui.theme.MagpieTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MagpieTheme {
                MagpieAppScaffold()
            }
        }
    }
}
