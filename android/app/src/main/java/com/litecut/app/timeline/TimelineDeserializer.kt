package com.litecut.app.timeline

import android.util.Log
import org.json.JSONObject

class TimelineDeserializer {

    /**
     * Reconstructs and injects the deserialized ProjectDocument back into the central TimelineEngine.
     */
    fun deserialize(document: ProjectDocument, timelineEngine: TimelineEngine) {
        timelineEngine.clear()
        
        // 1. Rebuild and load layers/clips
        val timelineData = document.timelineData
        timelineEngine.loadFromProjectJSON(timelineData.toString())
        
        // 2. Restore playback UI states
        val playback = document.playbackSettings
        timelineEngine.currentTime = playback.optDouble("currentTime", 0.0)
        timelineEngine.zoomLevel = playback.optDouble("zoomLevel", 1.0)
        timelineEngine.scrollLeft = playback.optDouble("scrollLeft", 0.0)
        timelineEngine.pixelsPerSecond = playback.optDouble("pixelsPerSecond", 100.0)
        
        // 3. Restore selected clip IDs if present
        if (timelineData.has("selectedClipIds")) {
            val selArr = timelineData.getJSONArray("selectedClipIds")
            timelineEngine.selectedClipIds.clear()
            for (i in 0 until selArr.length()) {
                timelineEngine.selectedClipIds.add(selArr.getString(i))
            }
        }
        
        Log.i("TimelineDeserializer", "Project timeline deserialized and injected successfully.")
    }
}
