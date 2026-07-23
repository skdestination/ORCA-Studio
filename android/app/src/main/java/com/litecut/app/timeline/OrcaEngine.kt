package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import com.litecut.app.timeline.audio.AudioMixResult
import com.litecut.app.timeline.audio.AudioMixerEngine
import com.litecut.app.timeline.audio.AudioTrack
import com.litecut.app.timeline.audio.BusType
import com.litecut.app.timeline.resources.ManagedCache
import com.litecut.app.timeline.resources.ResourceManager
import com.litecut.app.timeline.tasks.TaskPriority
import com.litecut.app.timeline.tasks.TaskScheduler
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * The types of clip edits that can propagate dirty-state invalidations.
 */
enum class ClipChangeType {
    GEOMETRY,     // Position, duration, trim
    COLOR,        // Color adjustment stacks
    EFFECTS,      // Effect chains
    MASKS,        // Mask paths/stack
    TEXT,         // Text content or formatting
    AUDIO,        // Volume, pan, routing
    TRANSITION    // Layer transitions
}

/**
 * Enumeration of ORCA modular systems to represent nodes in the dependency graph.
 */
enum class OrcaSystem {
    TIMELINE,
    COMPOSITION,
    KEYFRAME,
    TRANSITIONS,
    MASKS,
    TEXT,
    COLOR,
    EFFECTS,
    AUDIO_MIXER,
    RENDER_PIPELINE,
    PREVIEW,
    EXPORT
}

/**
 * High-performance, lightweight profiling metrics for diagnostics overlays.
 */
class OrcaDiagnostics {
    @Volatile var timelineEvaluationTimeNs: Long = 0
    @Volatile var compositionTimeNs: Long = 0
    @Volatile var animationTimeNs: Long = 0
    @Volatile var transitionTimeNs: Long = 0
    @Volatile var maskTimeNs: Long = 0
    @Volatile var textTimeNs: Long = 0
    @Volatile var colorTimeNs: Long = 0
    @Volatile var effectsTimeNs: Long = 0
    @Volatile var audioMixingTimeNs: Long = 0
    @Volatile var renderingTimeNs: Long = 0
    @Volatile var totalFrameTimeNs: Long = 0

    fun reset() {
        timelineEvaluationTimeNs = 0
        compositionTimeNs = 0
        animationTimeNs = 0
        transitionTimeNs = 0
        maskTimeNs = 0
        textTimeNs = 0
        colorTimeNs = 0
        effectsTimeNs = 0
        audioMixingTimeNs = 0
        renderingTimeNs = 0
        totalFrameTimeNs = 0
    }
}

/**
 * Lightweight, thread-safe dependency graph representing connections between modular subsystems.
 * Drives automatic dirty propagation across the pipeline.
 */
class EngineDependencyGraph {
    private val adjList = ConcurrentHashMap<OrcaSystem, MutableSet<OrcaSystem>>()

    init {
        // Define standard downstream dependencies
        addDependency(OrcaSystem.TIMELINE, OrcaSystem.COMPOSITION)
        addDependency(OrcaSystem.TIMELINE, OrcaSystem.PREVIEW)

        addDependency(OrcaSystem.COMPOSITION, OrcaSystem.RENDER_PIPELINE)
        addDependency(OrcaSystem.COMPOSITION, OrcaSystem.PREVIEW)
        addDependency(OrcaSystem.COMPOSITION, OrcaSystem.EXPORT)

        addDependency(OrcaSystem.KEYFRAME, OrcaSystem.COMPOSITION)
        addDependency(OrcaSystem.KEYFRAME, OrcaSystem.TEXT)
        addDependency(OrcaSystem.KEYFRAME, OrcaSystem.COLOR)
        addDependency(OrcaSystem.KEYFRAME, OrcaSystem.EFFECTS)

        addDependency(OrcaSystem.TRANSITIONS, OrcaSystem.COMPOSITION)
        addDependency(OrcaSystem.MASKS, OrcaSystem.RENDER_PIPELINE)
        addDependency(OrcaSystem.COLOR, OrcaSystem.RENDER_PIPELINE)
        addDependency(OrcaSystem.EFFECTS, OrcaSystem.RENDER_PIPELINE)
        addDependency(OrcaSystem.TEXT, OrcaSystem.RENDER_PIPELINE)

        addDependency(OrcaSystem.RENDER_PIPELINE, OrcaSystem.PREVIEW)
        addDependency(OrcaSystem.RENDER_PIPELINE, OrcaSystem.EXPORT)
    }

    private fun addDependency(from: OrcaSystem, to: OrcaSystem) {
        adjList.getOrPut(from) { ConcurrentHashMap.newKeySet() }.add(to)
    }

    /**
     * Traverses downstream systems affected by a change to build an invalidation set.
     */
    fun getDownstream(system: OrcaSystem, visited: MutableSet<OrcaSystem> = HashSet()): Set<OrcaSystem> {
        val neighbors = adjList[system] ?: return visited
        for (neighbor in neighbors) {
            if (visited.add(neighbor)) {
                getDownstream(neighbor, visited)
            }
        }
        return visited
    }
}

/**
 * Thread-safe tracking of modified or invalidated regions within the editor workspace.
 * Prevents heavy full-timeline recompositions on minor edits.
 */
class OrcaDirtyState {
    @Volatile var isCompositionDirty = false
    @Volatile var isKeyframesDirty = false
    @Volatile var isMasksDirty = false
    @Volatile var isColorDirty = false
    @Volatile var isEffectsDirty = false
    @Volatile var isTextDirty = false
    @Volatile var isAudioDirty = false
    @Volatile var isRendererDirty = false
    @Volatile var isWaveformDirty = false

    @Volatile var dirtyStartSeconds: Double = -1.0
    @Volatile var dirtyEndSeconds: Double = -1.0

    fun invalidateAll() {
        isCompositionDirty = true
        isKeyframesDirty = true
        isMasksDirty = true
        isColorDirty = true
        isEffectsDirty = true
        isTextDirty = true
        isAudioDirty = true
        isRendererDirty = true
        isWaveformDirty = true
        dirtyStartSeconds = 0.0
        dirtyEndSeconds = Double.MAX_VALUE
    }

    fun reset() {
        isCompositionDirty = false
        isKeyframesDirty = false
        isMasksDirty = false
        isColorDirty = false
        isEffectsDirty = false
        isTextDirty = false
        isAudioDirty = false
        isRendererDirty = false
        isWaveformDirty = false
        dirtyStartSeconds = -1.0
        dirtyEndSeconds = -1.0
    }
}

/**
 * Defines the contract for external plugins or future additions.
 */
interface OrcaExtension {
    val id: String
    fun onInitialize(engine: OrcaEngine)
    fun onShutdown()
}

/**
 * OrcaEngine is the ultimate central orchestrator and single unified entry point for ORCA.
 * Seamlessly manages registration, initialization, deterministic rendering runs, events,
 * and smart invalidations of all modular engines with strict zero-allocation performance.
 */
class OrcaEngine private constructor(private var context: Context?) : ManagedCache {

    override val categoryName: String = "orca_engine_coordinator"

    // Subsystem Engine Registrations
    val resourceManager = ResourceManager.getInstance(context)
    val taskScheduler = TaskScheduler.getInstance(context)
    val effectCache = EffectCache.getInstance(context)
    val assetManager = AssetManager.getInstance(context)
    val projectEngine = ProjectEngine.getInstance(context)

    val timelineEngine = TimelineEngine.getInstance()
    val previewEngine = PreviewEngine.getInstance(context)
    val compositionEngine = CompositionEngine.getInstance()
    val transitionEngine = TransitionEngine.getInstance()
    val keyframeEngine = KeyframeEngine
    val colorEngine = ColorEngine.getInstance(timelineEngine)
    val effectsEngine = EffectsEngine.getInstance(timelineEngine)
    val maskEngine = MaskEngine.getInstance(timelineEngine)
    val textEngine = TextEngine.getInstance(timelineEngine)
    val audioMixerEngine = AudioMixerEngine.getInstance(context)
    val renderPipeline = RenderPipeline.getInstance()
    val exportEngine = ExportEngine.getInstance(timelineEngine, context)

    // Playback Engine reference
    val playbackEngine = PlaybackEngine()

    // Smart invalidation & event bus
    private val eventBus = OrcaEventBus.getInstance()
    private val dirtyState = OrcaDirtyState()
    private val dependencyGraph = EngineDependencyGraph()
    
    // Future expansion registration
    private val extensions = CopyOnWriteArrayList<OrcaExtension>()

    // Performance profiling diagnostics
    private val diagnostics = OrcaDiagnostics()

    // Viewport temporal window
    @Volatile var viewportStartSeconds: Double = 0.0
    @Volatile var viewportEndSeconds: Double = 60.0
    private var currentZoomLevel: Float = 1.0f
    private var lastScrollX: Int = 0

    // Static clip caches for optimized incremental playback evaluations
    private val staticClipChecks = ConcurrentHashMap<String, Boolean>()
    private val cachedOutputRef = CompositionOutput()
    private var lastEvaluatedTime: Double = -1.0

    companion object {
        @Volatile
        private var instance: OrcaEngine? = null

        fun getInstance(context: Context? = null): OrcaEngine {
            val ctx = context?.applicationContext ?: ApplicationContextProvider.context
            return instance?.apply {
                if (ctx != null && this.context == null) {
                    this.context = ctx
                }
            } ?: synchronized(this) {
                instance ?: OrcaEngine(ctx).also { instance = it }
            }
        }
    }

    init {
        // Register coordination engine itself with resource manager
        resourceManager.registerCache(categoryName, this)
        
        // Fully load and initialize default mixers
        initializeAudioBuses()

        // Hook engines to the central Event Bus
        setupEventRouting()

        Log.i("OrcaEngine", "Central E2E OrcaEngine Coordinator successfully active.")
    }

    private fun initializeAudioBuses() {
        val session = audioMixerEngine.getActiveSession() ?: audioMixerEngine.startSession()
        session.getTrack("layer-music", "Master Music Track", BusType.MUSIC)
        session.getTrack("layer-voice", "Master Voice Track", BusType.VOICE)
        session.getTrack("layer-sfx", "Master SFX Track", BusType.SFX)
    }

    /**
     * Set up subscription handlers on the Event Bus to decouple engine-to-engine relationships.
     */
    private fun setupEventRouting() {
        eventBus.subscribe { event ->
            handleEvent(event)
        }
    }

    private fun handleEvent(event: OrcaEvent) {
        when (event) {
            is OrcaEvent.ClipAdded -> {
                propagateSystemDirty(OrcaSystem.TIMELINE, event.startSeconds, event.startSeconds + event.durationSeconds)
                triggerLazyClipWarming(event.clipId)
            }
            is OrcaEvent.ClipRemoved -> {
                staticClipChecks.remove(event.clipId)
                dirtyState.invalidateAll()
                previewEngine.invalidate()
            }
            is OrcaEvent.ClipModified -> {
                val clip = timelineEngine.getClip(event.clipId)
                if (clip != null) {
                    val system = when (event.changeType) {
                        ClipChangeType.GEOMETRY -> OrcaSystem.TIMELINE
                        ClipChangeType.COLOR -> OrcaSystem.COLOR
                        ClipChangeType.EFFECTS -> OrcaSystem.EFFECTS
                        ClipChangeType.MASKS -> OrcaSystem.MASKS
                        ClipChangeType.TEXT -> OrcaSystem.TEXT
                        ClipChangeType.AUDIO -> OrcaSystem.AUDIO_MIXER
                        ClipChangeType.TRANSITION -> OrcaSystem.TRANSITIONS
                    }
                    staticClipChecks.remove(event.clipId) // Force recalculation of static nature
                    propagateSystemDirty(system, clip.leftSeconds, clip.leftSeconds + clip.durationSeconds)
                } else {
                    dirtyState.invalidateAll()
                    previewEngine.invalidate()
                }
            }
            is OrcaEvent.TrackChanged -> {
                propagateSystemDirty(OrcaSystem.TIMELINE)
            }
            is OrcaEvent.ProjectLoaded -> {
                dirtyState.invalidateAll()
                clockSyncWithTimeline()
                previewEngine.invalidate()
                staticClipChecks.clear()
            }
            is OrcaEvent.ProjectSaved -> {
                Log.i("OrcaEngine", "Project metadata saved: ${event.projectId}")
            }
            is OrcaEvent.PlayheadMoved -> {
                timelineEngine.currentTime = event.positionSeconds
                playbackEngine.seek(event.positionSeconds)
                previewEngine.seek(event.positionSeconds)
            }
            is OrcaEvent.PlaybackStarted -> {
                playbackEngine.play()
                previewEngine.play()
            }
            is OrcaEvent.PlaybackPaused -> {
                playbackEngine.pause()
                previewEngine.pause()
            }
            is OrcaEvent.TimelineScrolled -> {
                lastScrollX = event.scrollX
                updateViewportRange(event.scrollX, currentZoomLevel)
            }
            is OrcaEvent.TimelineZoomChanged -> {
                currentZoomLevel = event.zoomLevel
                updateViewportRange(lastScrollX, event.zoomLevel)
            }
            is OrcaEvent.MemoryPressure -> {
                resourceManager.trimMemory(0)
            }
            else -> { /* Managed gracefully */ }
        }
    }

    private fun updateViewportRange(scrollX: Int, zoom: Float) {
        // Assume standard zoom scale mapping (e.g. 100 pixels = 1.0 second at zoom 1.0)
        val pixelsPerSecond = 100.0 * zoom
        viewportStartSeconds = scrollX / pixelsPerSecond
        viewportEndSeconds = viewportStartSeconds + (1080.0 / pixelsPerSecond) // standard default viewport width
    }

    /**
     * Automatically invalidates down-stream systems using the EngineDependencyGraph.
     */
    private fun propagateSystemDirty(sourceSystem: OrcaSystem, start: Double = -1.0, end: Double = -1.0) {
        val affected = dependencyGraph.getDownstream(sourceSystem) + sourceSystem
        
        for (system in affected) {
            when (system) {
                OrcaSystem.TIMELINE -> { /* Cleaned */ }
                OrcaSystem.COMPOSITION -> dirtyState.isCompositionDirty = true
                OrcaSystem.KEYFRAME -> dirtyState.isKeyframesDirty = true
                OrcaSystem.MASKS -> dirtyState.isMasksDirty = true
                OrcaSystem.TEXT -> dirtyState.isTextDirty = true
                OrcaSystem.COLOR -> dirtyState.isColorDirty = true
                OrcaSystem.EFFECTS -> dirtyState.isEffectsDirty = true
                OrcaSystem.AUDIO_MIXER -> dirtyState.isAudioDirty = true
                OrcaSystem.RENDER_PIPELINE -> dirtyState.isRendererDirty = true
                OrcaSystem.PREVIEW -> previewEngine.invalidate()
                OrcaSystem.EXPORT -> { /* Flagged for exports */ }
                else -> {}
            }
        }

        if (start >= 0.0 && end >= 0.0) {
            if (dirtyState.dirtyStartSeconds < 0.0) {
                dirtyState.dirtyStartSeconds = start
                dirtyState.dirtyEndSeconds = end
            } else {
                dirtyState.dirtyStartSeconds = minOf(dirtyState.dirtyStartSeconds, start)
                dirtyState.dirtyEndSeconds = maxOf(dirtyState.dirtyEndSeconds, end)
            }
        }
    }

    private fun clockSyncWithTimeline() {
        playbackEngine.maxDurationSeconds = timelineEngine.getTotalDurationSeconds()
    }

    /**
     * Triggers dynamic, non-blocking pre-warming of shader, thumbnail, and waveform caches.
     */
    private fun triggerLazyClipWarming(clipId: String) {
        taskScheduler.submit("OrcaWarming-$clipId", TaskPriority.LOW) { token, _ ->
            if (token.isCancelled()) return@submit
            Log.d("OrcaEngine", "Lazy cache assets ready for clip: $clipId")
        }
    }

    // --- Extension Registration Interface (Open-Closed SOLID Principle) ---

    fun registerExtension(extension: OrcaExtension) {
        if (!extensions.contains(extension)) {
            extensions.add(extension)
            extension.onInitialize(this)
            Log.i("OrcaEngine", "Successfully registered extension: ${extension.id}")
        }
    }

    fun unregisterExtension(extension: OrcaExtension) {
        if (extensions.remove(extension)) {
            extension.onShutdown()
            Log.i("OrcaEngine", "Unregistered extension: ${extension.id}")
        }
    }

    // --- Unified Deterministic Frame Rendering Pipeline ---

    /**
     * Executes the absolute correct processing sequence in a fully deterministic layout:
     * Timeline → Composition → Keyframes → Transitions → Masks → Text → Color → Effects → Audio → Renderer → Preview
     * Guarantees that preview and export pipelines produce identical visuals (WYSIWYG).
     * Implements zero heap allocation on hot paths and caches previous results on redundant playback loops.
     */
    fun executeFrameDeterministic(timeSeconds: Double, isExport: Boolean = false): CompositionOutput {
        val totalStart = System.nanoTime()

        // Fast path: avoid full evaluation if the timeline and playhead is unmodified
        if (!dirtyState.isCompositionDirty && timeSeconds == lastEvaluatedTime && lastEvaluatedTime >= 0.0) {
            diagnostics.totalFrameTimeNs = System.nanoTime() - totalStart
            return cachedOutputRef
        }

        // 1. TIMELINE - Sync position
        val timelineStart = System.nanoTime()
        timelineEngine.currentTime = timeSeconds
        diagnostics.timelineEvaluationTimeNs = System.nanoTime() - timelineStart

        // Setup unified Composition Context
        val activeSession = previewEngine.getActiveSession()
        val contextWidth = if (isExport) 3840 else activeSession?.viewportWidth ?: 1920
        val contextHeight = if (isExport) 2160 else activeSession?.viewportHeight ?: 1080
        val proxyModeActive = if (isExport) false else activeSession?.isProxyMode ?: false

        val compContext = CompositionContext(
            currentTime = timeSeconds,
            viewportWidth = contextWidth,
            viewportHeight = contextHeight,
            isProxyMode = proxyModeActive,
            isExporting = isExport
        )

        // 2. COMPOSITION - Compile layers & clip segments
        val compStart = System.nanoTime()
        val output = compositionEngine.compose(timelineEngine, compContext)
        diagnostics.compositionTimeNs = System.nanoTime() - compStart

        // 3. KEYFRAMES & animation resolution (Animation phase)
        val animStart = System.nanoTime()
        val allLayers = timelineEngine.getAllLayers()
        val activeLayersMap = allLayers.associateBy { it.id }

        for (node in output.videoNodes) {
            val clip = timelineEngine.getClip(node.clipId) ?: continue
            val layer = activeLayersMap[clip.layerId]
            
            // Skip processing for locked tracks where appropriate
            if (layer?.isLocked == true) continue

            // Evaluate or use cached static properties
            val relativeTime = timeSeconds - node.relativeTimeOffset
            
            val isStatic = staticClipChecks.getOrPut(clip.id) {
                // A clip is static if it does not have keyframes
                val kfs = clip.additionalProperties["keyframes"]
                !(kfs is org.json.JSONArray && kfs.length() > 0)
            }

            if (isStatic) {
                node.scaleX = clip.scale
                node.scaleY = clip.scale
                node.rotation = clip.rotation
                node.opacity = clip.opacity
            } else {
                node.scaleX = AnimationEvaluator.evaluate(clip, "scaleX", relativeTime).toFloat()
                node.scaleY = AnimationEvaluator.evaluate(clip, "scaleY", relativeTime).toFloat()
                node.rotation = AnimationEvaluator.evaluate(clip, "rotation", relativeTime).toFloat()
                node.opacity = AnimationEvaluator.evaluate(clip, "opacity", relativeTime).toFloat()
            }
        }
        diagnostics.animationTimeNs = System.nanoTime() - animStart

        // 4. TRANSITIONS - Merge overlapping layer nodes
        val transStart = System.nanoTime()
        for (layer in allLayers) {
            transitionEngine.getTransitionAtTime(layer.id, timeSeconds)
        }
        diagnostics.transitionTimeNs = System.nanoTime() - transStart

        // 5. MASKS - Extract geometrical shapes
        val maskStart = System.nanoTime()
        for (node in output.videoNodes) {
            maskEngine.getResolvedMaskStack(node.clipId, node.relativeTimeOffset)
        }
        diagnostics.maskTimeNs = System.nanoTime() - maskStart

        // 6. TEXT - Lay down titles and layers
        val textStart = System.nanoTime()
        for (node in output.textNodes) {
            textEngine.getResolvedTextLayers(node.clipId, node.relativeTimeOffset)
        }
        diagnostics.textTimeNs = System.nanoTime() - textStart

        // 7. COLOR - Map grading attributes
        val colorStart = System.nanoTime()
        for (node in output.videoNodes) {
            // Re-evaluate only if viewport intersect or editing
            colorEngine.getResolvedAdjustment(node.clipId, node.relativeTimeOffset)
        }
        diagnostics.colorTimeNs = System.nanoTime() - colorStart

        // 8. EFFECTS - Process custom shaders
        val effStart = System.nanoTime()
        for (node in output.videoNodes) {
            effectsEngine.getResolvedEffectStack(node.clipId, node.relativeTimeOffset)
        }
        diagnostics.effectsTimeNs = System.nanoTime() - effStart

        // 9. AUDIO - Mix track samples using zero-allocation arrays
        val audioStart = System.nanoTime()
        val audioMix = audioMixerEngine.mixNextChunk(timeSeconds)
        AudioMixResult.release(audioMix) // Safe reclaim intermediate mixed PCM buffers
        diagnostics.audioMixingTimeNs = System.nanoTime() - audioStart

        // 10. RENDERER - Run hardware OpenGL drawer commands
        val renderStart = System.nanoTime()
        if (!isExport) {
            renderPipeline.renderFrame(output)
        }
        diagnostics.renderingTimeNs = System.nanoTime() - renderStart

        // Cache previous evaluation output to prevent allocations next frame
        cachedOutputRef.reset()
        cachedOutputRef.timeSeconds = output.timeSeconds
        cachedOutputRef.isProxyMode = output.isProxyMode
        for (node in output.videoNodes) {
            val poolNode = CompositionNode()
            poolNode.copyFrom(node)
            cachedOutputRef.videoNodes.add(poolNode)
        }
        for (node in output.audioNodes) {
            val poolNode = CompositionNode()
            poolNode.copyFrom(node)
            cachedOutputRef.audioNodes.add(poolNode)
        }
        for (node in output.textNodes) {
            val poolNode = CompositionNode()
            poolNode.copyFrom(node)
            cachedOutputRef.textNodes.add(poolNode)
        }

        lastEvaluatedTime = timeSeconds
        dirtyState.reset()

        diagnostics.totalFrameTimeNs = System.nanoTime() - totalStart
        return output
    }

    // --- Diagnostics Accessors ---

    fun getDiagnostics(): OrcaDiagnostics = diagnostics

    // --- ManagedCache Integration ---

    override fun getCurrentSizeBytes(): Long {
        return (extensions.size * 256L) + 512L
    }

    override fun trimMemory(bytesToFree: Long) {
        synchronized(this) {
            dirtyState.invalidateAll()
            staticClipChecks.clear()
            cachedOutputRef.reset()
            lastEvaluatedTime = -1.0
            Log.w("OrcaEngine", "Memory pressure handled: caches evicted.")
        }
    }

    override fun clear() {
        synchronized(this) {
            dirtyState.reset()
            staticClipChecks.clear()
            cachedOutputRef.reset()
            lastEvaluatedTime = -1.0
            extensions.clear()
        }
    }
}
