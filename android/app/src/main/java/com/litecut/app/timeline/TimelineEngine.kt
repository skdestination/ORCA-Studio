package com.litecut.app.timeline

import org.json.JSONObject
import org.json.JSONArray
import java.util.Stack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TimelineEngine {
    private val clips = LinkedHashMap<String, Clip>()
    private val layers = ArrayList<Layer>()
    
    // UI state synchronized native-side
    var currentTime: Double = 0.0
    var zoomLevel: Double = 1.0
    var scrollLeft: Double = 0.0
    var pixelsPerSecond: Double = 100.0
    val selectedClipIds = HashSet<String>()
    
    // Undo/Redo preparation
    private val undoStack = Stack<Command>()
    private val redoStack = Stack<Command>()

    companion object {
        private var instance: TimelineEngine? = null
        
        @Synchronized
        fun getInstance(): TimelineEngine {
            if (instance == null) {
                instance = TimelineEngine()
            }
            return instance!!
        }
    }

    // --- State Serialization & Import ---

    fun loadFromProjectJSON(projectJsonStr: String) {
        clear()
        try {
            val root = JSONObject(projectJsonStr)
            
            // Load layers
            if (root.has("layers")) {
                val layersArr = root.getJSONArray("layers")
                for (i in 0 until layersArr.length()) {
                    val layerObj = layersArr.getJSONObject(i)
                    layers.add(Layer.fromJSONObject(layerObj))
                }
            }
            
            // Load clips
            if (root.has("clips")) {
                val clipsArr = root.getJSONArray("clips")
                for (i in 0 until clipsArr.length()) {
                    val clipObj = clipsArr.getJSONObject(i)
                    val clip = Clip.fromJSONObject(clipObj)
                    clips[clip.id] = clip
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProjectJSON(): JSONObject {
        val root = JSONObject()
        val layersArr = JSONArray()
        for (layer in layers) {
            layersArr.put(layer.toJSONObject())
        }
        root.put("layers", layersArr)

        val clipsArr = JSONArray()
        for (clip in clips.values) {
            clipsArr.put(clip.toJSONObject())
        }
        root.put("clips", clipsArr)
        
        root.put("currentTime", currentTime)
        root.put("zoomLevel", zoomLevel)
        root.put("scrollLeft", scrollLeft)
        
        val selArr = JSONArray()
        for (selId in selectedClipIds) {
            selArr.put(selId)
        }
        root.put("selectedClipIds", selArr)

        return root
    }

    fun clear() {
        clips.clear()
        layers.clear()
        selectedClipIds.clear()
        undoStack.clear()
        redoStack.clear()
    }

    // --- Basic Getters ---

    fun getClip(id: String): Clip? = clips[id]
    
    fun getAllClips(): List<Clip> = clips.values.toList()
    
    fun getAllLayers(): List<Layer> = layers.toList()

    fun getTotalDurationSeconds(): Double {
        var maxTime = 0.0
        for (clip in clips.values) {
            val end = clip.leftSeconds + clip.durationSeconds
            if (end > maxTime) {
                maxTime = end
            }
        }
        return max(maxTime, 30.0) // Return at least 30 seconds default
    }

    // --- Undo/Redo Execution ---

    fun executeCommand(command: Command) {
        command.execute(this)
        undoStack.push(command)
        redoStack.clear() // Clear redo stack on new actions
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val cmd = undoStack.pop()
            cmd.undo(this)
            redoStack.push(cmd)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val cmd = redoStack.pop()
            cmd.execute(this)
            undoStack.push(cmd)
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // --- Internal Operations (Called by commands) ---

    fun addClipInternal(clip: Clip) {
        clips[clip.id] = clip
    }

    fun deleteClipInternal(id: String) {
        clips.remove(id)
        selectedClipIds.remove(id)
    }

    fun deleteClipsInternal(ids: List<String>) {
        for (id in ids) {
            clips.remove(id)
            selectedClipIds.remove(id)
        }
    }

    fun moveClipsInternal(
        clipIds: List<String>,
        deltaSeconds: Double,
        targetLayerId: String,
        fallbackLayerId: String
    ) {
        if (clipIds.isEmpty()) return
        
        // Single clip logic
        if (clipIds.size == 1) {
            val clipId = clipIds[0]
            val clip = clips[clipId] ?: return
            
            // Prevent going below 0
            val potentialLeft = max(0.0, clip.leftSeconds + deltaSeconds)
            
            // Check overlap on target layer
            val hasOverlapTarget = clips.values.any { other ->
                other.id != clipId &&
                other.layerId == targetLayerId &&
                potentialLeft < other.leftSeconds + other.durationSeconds &&
                potentialLeft + clip.durationSeconds > other.leftSeconds
            }

            if (!hasOverlapTarget) {
                clip.leftSeconds = potentialLeft
                clip.layerId = targetLayerId
            } else {
                // Check overlap on fallback (old) layer
                val hasOverlapFallback = clips.values.any { other ->
                    other.id != clipId &&
                    other.layerId == fallbackLayerId &&
                    potentialLeft < other.leftSeconds + other.durationSeconds &&
                    potentialLeft + clip.durationSeconds > other.leftSeconds
                }
                if (!hasOverlapFallback) {
                    clip.leftSeconds = potentialLeft
                    clip.layerId = fallbackLayerId
                }
            }
        } else {
            // Multi clip drag logic
            val pivotClip = clips[clipIds[0]] ?: return
            val sortedLayers = layers.sortedByDescending { it.order }
            
            val originalPivotLayerIndex = sortedLayers.indexOfFirst { it.id == pivotClip.layerId }
            val targetPivotLayerIndex = sortedLayers.indexOfFirst { it.id == targetLayerId }
            val layerOffset = if (originalPivotLayerIndex != -1 && targetPivotLayerIndex != -1) {
                targetPivotLayerIndex - originalPivotLayerIndex
            } else 0

            // Determine if there would be overlap
            val wouldOverlap = clips.values.any { c ->
                if (c.id in clipIds) false
                else {
                    clipIds.any { selId ->
                        val selClip = clips[selId] ?: return@any false
                        val proposedLeft = max(0.0, selClip.leftSeconds + deltaSeconds)
                        
                        val initLayerIndex = sortedLayers.indexOfFirst { it.id == selClip.layerId }
                        var proposedLayerId = selClip.layerId
                        if (initLayerIndex != -1 && layerOffset != 0) {
                            val targetIndex = max(0, min(sortedLayers.size - 1, initLayerIndex + layerOffset))
                            proposedLayerId = sortedLayers[targetIndex].id
                        }

                        c.layerId == proposedLayerId &&
                        proposedLeft < c.leftSeconds + c.durationSeconds &&
                        proposedLeft + selClip.durationSeconds > c.leftSeconds
                    }
                }
            }

            if (!wouldOverlap) {
                for (id in clipIds) {
                    val c = clips[id] ?: continue
                    c.leftSeconds = max(0.0, c.leftSeconds + deltaSeconds)
                    
                    val initLayerIndex = sortedLayers.indexOfFirst { it.id == c.layerId }
                    if (initLayerIndex != -1 && layerOffset != 0) {
                        val targetIndex = max(0, min(sortedLayers.size - 1, initLayerIndex + layerOffset))
                        c.layerId = sortedLayers[targetIndex].id
                    }
                }
            }
        }
    }

    fun trimClipInternal(
        clipId: String,
        side: String,
        deltaSeconds: Double,
        snappingEnabled: Boolean,
        currentTime: Double
    ) {
        val clip = clips[clipId] ?: return
        val initialLeft = clip.leftSeconds
        val initialDuration = clip.durationSeconds
        val initialTrimStart = clip.trimStartSeconds
        val speed = clip.speed

        if (side == "left") {
            var newLeft = max(0.0, initialLeft + deltaSeconds)
            
            if (snappingEnabled) {
                newLeft = snapValue(newLeft, clipId, currentTime)
            }
            
            val change = newLeft - initialLeft
            
            if (clip.type == ClipType.IMAGE || clip.type == ClipType.TEXT) {
                val newDuration = max(0.5, initialDuration - change)
                if (newDuration >= 0.5) {
                    val hasOverlap = clips.values.any { other ->
                        other.id != clipId &&
                        other.layerId == clip.layerId &&
                        newLeft < other.leftSeconds + other.durationSeconds &&
                        newLeft + newDuration > other.leftSeconds
                    }
                    if (!hasOverlap) {
                        clip.leftSeconds = newLeft
                        clip.durationSeconds = newDuration
                        
                        // Handle keyframes
                        val kfs = clip.additionalProperties["keyframes"]
                        if (kfs is JSONArray) {
                            clip.additionalProperties["keyframes"] = KeyframeEngine.trimKeyframes(kfs, change)
                        }
                    }
                }
            } else {
                // Video or Audio with speed multiplier
                var newTrimStart = initialTrimStart + change * speed
                var newDuration = initialDuration - change
                var finalLeft = newLeft

                if (newTrimStart < 0.0) {
                    newTrimStart = 0.0
                    finalLeft = initialLeft - initialTrimStart / speed
                    newDuration = initialDuration + initialTrimStart / speed
                }

                if (newDuration >= 0.5) {
                    val hasOverlap = clips.values.any { other ->
                        other.id != clipId &&
                        other.layerId == clip.layerId &&
                        max(0.0, finalLeft) < other.leftSeconds + other.durationSeconds &&
                        max(0.0, finalLeft) + newDuration > other.leftSeconds
                    }
                    if (!hasOverlap) {
                        clip.leftSeconds = max(0.0, finalLeft)
                        clip.trimStartSeconds = newTrimStart
                        clip.durationSeconds = newDuration
                    }
                }
            }
        } else {
            // Right Side Trimming
            val maxAvailable = clip.originalDurationSeconds ?: Double.MAX_VALUE
            var newDuration = max(0.5, initialDuration + deltaSeconds)
            
            if (clip.type == ClipType.IMAGE || clip.type == ClipType.TEXT) {
                if (snappingEnabled) {
                    val snappedRight = snapValue(initialLeft + newDuration, clipId, currentTime)
                    newDuration = max(0.5, snappedRight - initialLeft)
                }
                val hasOverlap = clips.values.any { other ->
                    other.id != clipId &&
                    other.layerId == clip.layerId &&
                    initialLeft < other.leftSeconds + other.durationSeconds &&
                    initialLeft + newDuration > other.leftSeconds
                }
                if (!hasOverlap) {
                    clip.durationSeconds = newDuration
                }
            } else {
                // Video/Audio right trim
                if (initialTrimStart + newDuration * speed > maxAvailable) {
                    newDuration = (maxAvailable - initialTrimStart) / speed
                }
                if (snappingEnabled) {
                    val snappedRight = snapValue(initialLeft + newDuration, clipId, currentTime)
                    newDuration = max(0.5, snappedRight - initialLeft)
                    if (initialTrimStart + newDuration * speed > maxAvailable) {
                        newDuration = (maxAvailable - initialTrimStart) / speed
                    }
                }
                val hasOverlap = clips.values.any { other ->
                    other.id != clipId &&
                    other.layerId == clip.layerId &&
                    initialLeft < other.leftSeconds + other.durationSeconds &&
                    initialLeft + newDuration > other.leftSeconds
                }
                if (!hasOverlap) {
                    clip.durationSeconds = newDuration
                }
            }
        }
    }

    fun splitClipInternal(clipId: String, splitTime: Double, generatedNewId: String): Clip? {
        val clip = clips[clipId] ?: return null
        
        if (splitTime <= clip.leftSeconds || splitTime >= clip.leftSeconds + clip.durationSeconds) {
            return null
        }

        val originalDuration = clip.durationSeconds
        val leftDuration = splitTime - clip.leftSeconds
        val rightDuration = originalDuration - leftDuration

        // Update left clip duration
        clip.durationSeconds = leftDuration

        // Handle keyframes
        val kfs = clip.additionalProperties["keyframes"]
        if (kfs is JSONArray) {
            val (leftKfs, rightKfs) = KeyframeEngine.splitKeyframes(kfs, leftDuration)
            clip.additionalProperties["keyframes"] = leftKfs
            
            // Create right clip
            val newTrimStart = clip.trimStartSeconds + leftDuration * clip.speed
            val newClip = Clip(
                id = generatedNewId,
                layerId = clip.layerId,
                type = clip.type,
                src = clip.src,
                name = clip.name?.let { "$it (Split)" } ?: "Split Clip",
                leftSeconds = splitTime,
                durationSeconds = rightDuration,
                trimStartSeconds = newTrimStart,
                originalDurationSeconds = clip.originalDurationSeconds,
                speed = clip.speed,
                additionalProperties = clip.additionalProperties.toMutableMap()
            )
            newClip.additionalProperties["keyframes"] = rightKfs
            
            clips[newClip.id] = newClip
            return newClip
        }

        // Create right clip (no keyframes)
        val newTrimStart = clip.trimStartSeconds + leftDuration * clip.speed
        val newClip = Clip(
            id = generatedNewId,
            layerId = clip.layerId,
            type = clip.type,
            src = clip.src,
            name = clip.name?.let { "$it (Split)" } ?: "Split Clip",
            leftSeconds = splitTime,
            durationSeconds = rightDuration,
            trimStartSeconds = newTrimStart,
            originalDurationSeconds = clip.originalDurationSeconds,
            speed = clip.speed,
            additionalProperties = clip.additionalProperties.toMutableMap()
        )

        clips[newClip.id] = newClip
        return newClip
    }

    fun rippleDeleteClipInternal(clipId: String) {
        val clip = clips[clipId] ?: return
        val deleteLeft = clip.leftSeconds
        val deleteDur = clip.durationSeconds
        val targetLayer = clip.layerId

        // Remove the clip
        clips.remove(clipId)
        selectedClipIds.remove(clipId)

        // Shift subsequent clips on the same layer to the left
        for (other in clips.values) {
            if (other.layerId == targetLayer && other.leftSeconds > deleteLeft) {
                other.leftSeconds = max(0.0, other.leftSeconds - deleteDur)
            }
        }
    }

    // --- Helper Snapping Algorithm ---

    private fun snapValue(proposedSeconds: Double, excludeClipId: String, currentTime: Double): Double {
        val threshold = 15.0 / pixelsPerSecond
        var minDistance = threshold
        var snappedValue = proposedSeconds

        val snapPoints = ArrayList<Double>()
        snapPoints.add(0.0)
        snapPoints.add(currentTime)

        for (c in clips.values) {
            if (c.id != excludeClipId) {
                snapPoints.add(c.leftSeconds)
                snapPoints.add(c.leftSeconds + c.durationSeconds)
            }
        }

        for (sp in snapPoints) {
            val dist = abs(sp - proposedSeconds)
            if (dist < minDistance) {
                minDistance = dist
                snappedValue = sp
            }
        }

        return snappedValue
    }

    // --- Layers & Tracks management ---

    fun createLayer(id: String, order: Int, name: String? = null): Layer {
        val layer = Layer(id, order, false, false, false, name)
        layers.add(layer)
        return layer
    }

    fun deleteLayer(id: String) {
        layers.removeAll { it.id == id }
        // Delete all clips on this layer too
        val toRemove = clips.filterValues { it.layerId == id }.keys
        for (cid in toRemove) {
            clips.remove(cid)
            selectedClipIds.remove(cid)
        }
    }

    fun reorderLayer(id: String, newOrder: Int) {
        layers.find { it.id == id }?.order = newOrder
    }

    // --- Coordinate conversions ---

    fun timeToPixel(time: Double): Double {
        return time * pixelsPerSecond
    }

    fun pixelToTime(pixel: Double): Double {
        return pixel / pixelsPerSecond
    }

    fun frameToTime(frame: Long, fps: Double): Double {
        return if (fps > 0.0) frame / fps else 0.0
    }

    fun timeToFrame(time: Double, fps: Double): Long {
        return if (fps > 0.0) (time * fps).toLong() else 0L
    }

    fun zoomToScale(zoom: Double): Double {
        // Expose current scale modifier for zoom
        return zoomLevel
    }
}
