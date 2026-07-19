package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

class LUTManager private constructor(private val context: Context) : ManagedCache {
    override val categoryName: String = "lut_cache"

    private val cachedLuts = ConcurrentHashMap<String, LUT>()
    private val loadingLuts = ConcurrentHashMap<String, Boolean>()

    companion object {
        @Volatile
        private var instance: LUTManager? = null

        fun getInstance(context: Context): LUTManager {
            return instance ?: synchronized(this) {
                instance ?: LUTManager(context.applicationContext).also { 
                    instance = it
                    // Register with ResourceManager
                    ResourceManager.getInstance(it.context).registerCache(it.categoryName, it)
                }
            }
        }
    }

    /**
     * Get or schedule asynchronous loading of a LUT.
     * Returns the LUT if already cached, or schedules background loading using TaskScheduler.
     */
    fun getOrLoadLut(lutId: String, name: String, filePath: String?, size: Int = 33, onComplete: (LUT?) -> Unit): LUT? {
        val existing = cachedLuts[lutId]
        if (existing != null && existing.lutData != null) {
            onComplete(existing)
            return existing
        }

        if (filePath == null) {
            // Identity/No LUT
            val emptyLut = LUT(lutId, name, null, size, 1.0f)
            cachedLuts[lutId] = emptyLut
            onComplete(emptyLut)
            return emptyLut
        }

        // Avoid duplicate concurrent loading tasks for the same LUT
        if (loadingLuts.putIfAbsent(lutId, true) == null) {
            val lutObj = LUT(lutId, name, filePath, size, 1.0f)
            
            // Dispatch asynchronously using TaskScheduler
            TaskScheduler.getInstance(context).submit(
                name = "LoadLUT-${lutId}",
                priority = TaskPriority.HIGH
            ) { token, progress ->
                try {
                    val file = File(filePath)
                    if (file.exists()) {
                        FileInputStream(file).use { stream ->
                            lutObj.parseCubeFile(stream)
                        }
                    }
                    lutObj
                } catch (e: Exception) {
                    Log.e("LUTManager", "Error parsing LUT file: $filePath", e)
                    null
                }
            }?.addListener(object : com.litecut.app.timeline.tasks.TaskHandle.TaskProgressListener {
                override fun onStateChanged(state: com.litecut.app.timeline.tasks.TaskState) {
                    if (state == com.litecut.app.timeline.tasks.TaskState.COMPLETED) {
                        if (lutObj.lutData != null) {
                            cachedLuts[lutId] = lutObj
                            onComplete(lutObj)
                        } else {
                            onComplete(null)
                        }
                        loadingLuts.remove(lutId)
                    } else if (state == com.litecut.app.timeline.tasks.TaskState.FAILED || state == com.litecut.app.timeline.tasks.TaskState.CANCELLED) {
                        onComplete(null)
                        loadingLuts.remove(lutId)
                    }
                }

                override fun onProgressUpdated(progress: Int) {}
            })
        }

        return cachedLuts[lutId]
    }

    override fun getCurrentSizeBytes(): Long {
        var bytes = 0L
        for (lut in cachedLuts.values) {
            val data = lut.lutData
            if (data != null) {
                bytes += data.size * 4L // floats (4 bytes)
            }
        }
        return bytes
    }

    override fun trimMemory(bytesToFree: Long) {
        var freed = 0L
        val keys = cachedLuts.keys()
        while (keys.hasMoreElements()) {
            if (freed >= bytesToFree) break
            val key = keys.nextElement()
            val lut = cachedLuts[key] ?: continue
            if (!lut.isBuiltIn) { // Prioritize keeping built-ins in memory
                val size = (lut.lutData?.size ?: 0) * 4L
                cachedLuts.remove(key)
                freed += size
            }
        }
        Log.d("LUTManager", "LUT Cache memory trim: Freed $freed bytes")
    }

    override fun clear() {
        cachedLuts.clear()
        loadingLuts.clear()
        Log.i("LUTManager", "Cleared LUT cache")
    }
}
