package com.litecut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.litecut.app.timeline.TimelineScreen

/**
 * Entry point of the ORCA Studio Pro native Android application.
 * Bypasses legacy web assets to render a 100% native editing experience with Jetpack Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize the splash screen transition before super.onCreate()
        installSplashScreen()

        // Ensure central OrcaEngine singleton instance is retrieved on startup
        com.litecut.app.timeline.OrcaEngine.getInstance()

        super.onCreate(savedInstanceState)

        // Enable edge-to-edge screen rendering to make the app full screen and draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Make the system bars completely transparent so app content flows underneath seamlessly
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Configure system bar icons: use light icons since our video editor is dark-themed
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        setContent {
            TimelineScreen(
                modifier = Modifier.fillMaxSize(),
                onBackClick = { finish() }
            )
        }
    }
}
