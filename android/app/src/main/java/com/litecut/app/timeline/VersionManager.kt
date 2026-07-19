package com.litecut.app.timeline

import android.util.Log
import org.json.JSONObject

class VersionManager {

    companion object {
        const val CURRENT_VERSION = 2
        private const val TAG = "VersionManager"
    }

    /**
     * Checks if a project format version is supported.
     * If the project's version is higher than supported, we flag a potential forward compatibility issue.
     */
    fun checkCompatibility(projectJson: JSONObject): CompatibilityResult {
        val metadata = projectJson.optJSONObject("metadata") ?: return CompatibilityResult.COMPATIBLE
        val fileVersion = metadata.optInt("creatorAppVersionCode", 1)

        return when {
            fileVersion == CURRENT_VERSION -> CompatibilityResult.COMPATIBLE
            fileVersion < CURRENT_VERSION -> CompatibilityResult.MIGRATION_REQUIRED
            else -> CompatibilityResult.FORWARD_COMPATIBILITY_WARNING
        }
    }

    /**
     * migrates older project JSON structures to the current schema definition.
     */
    fun migrate(projectJson: JSONObject): JSONObject {
        val metadata = projectJson.optJSONObject("metadata") ?: return projectJson
        var fileVersion = metadata.optInt("creatorAppVersionCode", 1)

        Log.i(TAG, "Starting project migration from version $fileVersion to $CURRENT_VERSION")

        while (fileVersion < CURRENT_VERSION) {
            when (fileVersion) {
                1 -> {
                    // Migration from v1 to v2: Introduce standard playhead configuration metrics
                    val playback = projectJson.optJSONObject("playbackSettings") ?: JSONObject()
                    if (!playback.has("pixelsPerSecond")) {
                        playback.put("pixelsPerSecond", 100.0)
                        projectJson.put("playbackSettings", playback)
                    }
                    fileVersion = 2
                    Log.d(TAG, "Successfully migrated project to v2 format.")
                }
                // Add future migrations here seamlessly
            }
        }

        // Update version marker
        metadata.put("creatorAppVersionCode", CURRENT_VERSION)
        metadata.put("creatorAppVersionName", "2.0.0")
        
        return projectJson
    }
}

enum class CompatibilityResult {
    COMPATIBLE,
    MIGRATION_REQUIRED,
    FORWARD_COMPATIBILITY_WARNING
}
