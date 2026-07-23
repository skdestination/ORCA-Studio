package com.litecut.app

import android.app.Application
import android.util.Log
import com.litecut.app.timeline.ApplicationContextProvider
import com.litecut.app.timeline.OrcaEngine

class OrcaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApplicationContextProvider.context = applicationContext
        Log.i("OrcaApplication", "Initializing central OrcaEngine singleton on Application startup...")
        try {
            OrcaEngine.getInstance(this)
            Log.i("OrcaApplication", "OrcaEngine successfully initialized on Application startup.")
        } catch (e: Exception) {
            Log.e("OrcaApplication", "CRITICAL: Failed to initialize OrcaEngine on Application startup", e)
        }
    }
}
