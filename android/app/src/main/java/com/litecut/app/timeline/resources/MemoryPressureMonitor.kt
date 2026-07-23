package com.litecut.app.timeline.resources

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log

class MemoryPressureMonitor(
    private val context: Context,
    private val onTrimCallback: (Int) -> Unit
) : ComponentCallbacks2 {

    private var registered = false

    fun start() {
        if (!registered) {
            try {
                context.registerComponentCallbacks(this)
                registered = true
                Log.d("MemoryPressureMonitor", "Successfully registered ComponentCallbacks2")
            } catch (e: Exception) {
                Log.e("MemoryPressureMonitor", "Failed to register memory monitor", e)
            }
        }
    }

    fun stop() {
        if (registered) {
            try {
                context.unregisterComponentCallbacks(this)
                registered = false
                Log.d("MemoryPressureMonitor", "Successfully unregistered ComponentCallbacks2")
            } catch (e: Exception) {
                Log.e("MemoryPressureMonitor", "Failed to unregister memory monitor", e)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op for memory monitoring
    }

    override fun onLowMemory() {
        Log.w("MemoryPressureMonitor", "Low memory signal received! Initiating critical TRIM_MEMORY_COMPLETE")
        onTrimCallback(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    override fun onTrimMemory(level: Int) {
        Log.d("MemoryPressureMonitor", "Trim memory signal received with level: $level")
        onTrimCallback(level)
    }
}
