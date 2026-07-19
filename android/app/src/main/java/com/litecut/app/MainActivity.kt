package com.litecut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.litecut.app.timeline.TimelineScreen

/**
 * Entry point of the ORCA Studio Pro native Android application.
 * Bypasses legacy web assets to render a 100% native editing experience with Jetpack Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimelineScreen(
                modifier = Modifier.fillMaxSize(),
                onBackClick = { finish() }
            )
        }
    }
}
