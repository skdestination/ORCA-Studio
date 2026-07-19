package com.litecut.app.timeline

import java.util.concurrent.ConcurrentLinkedQueue

class CompositionEngine private constructor() {
    
    // Concurrent pools to allow lock-free recycling across render/decoder/playback threads
    private val nodePool = ConcurrentLinkedQueue<CompositionNode>()
    private val layerPool = ConcurrentLinkedQueue<CompositionLayer>()
    private val graphPool = ConcurrentLinkedQueue<CompositionGraph>()
    
    // Stable pre-allocated output container representing active frame state
    private val activeOutput = CompositionOutput()

    companion object {
        @Volatile
        private var instance: CompositionEngine? = null

        fun getInstance(): CompositionEngine {
            return instance ?: synchronized(this) {
                instance ?: CompositionEngine().also { instance = it }
            }
        }
    }

    private fun obtainNode(): CompositionNode {
        return nodePool.poll() ?: CompositionNode()
    }

    private fun releaseNode(node: CompositionNode) {
        node.reset()
        nodePool.offer(node)
    }

    private fun obtainLayer(): CompositionLayer {
        return layerPool.poll() ?: CompositionLayer()
    }

    private fun releaseLayer(layer: CompositionLayer) {
        for (node in layer.nodes) {
            releaseNode(node)
        }
        layer.reset()
        layerPool.offer(layer)
    }

    private fun obtainGraph(): CompositionGraph {
        return graphPool.poll() ?: CompositionGraph()
    }

    private fun releaseGraph(graph: CompositionGraph) {
        for (layer in graph.layers) {
            releaseLayer(layer)
        }
        graph.reset()
        graphPool.offer(graph)
    }

    /**
     * Executes a full composition pass over the timeline data.
     * Extracts active clips, resolves keyframe properties, sorts tracks,
     * and compiles the final composition output in a zero-allocation fashion.
     */
    @Synchronized
    fun compose(
        timelineEngine: TimelineEngine,
        context: CompositionContext
    ): CompositionOutput {
        // Reset previous active output
        activeOutput.reset()
        activeOutput.timeSeconds = context.currentTime
        activeOutput.isProxyMode = context.isProxyMode

        val layers = timelineEngine.getAllLayers()
        val clips = timelineEngine.getAllClips()

        // Create mapping of active layers
        val activeLayersMap = HashMap<String, CompositionLayer>()
        
        // Setup composition layers from timeline engine layers state
        for (l in layers) {
            val compLayer = obtainLayer()
            compLayer.layerId = l.id
            compLayer.layerOrder = l.order
            compLayer.isHidden = l.isHidden
            compLayer.isMuted = l.isMuted
            activeLayersMap[l.id] = compLayer
        }

        // Process all active clips
        for (clip in clips) {
            val start = clip.leftSeconds
            val end = start + clip.durationSeconds
            
            // Check visibility intersection
            if (context.currentTime >= start && context.currentTime <= end) {
                val layerId = clip.layerId
                val compLayer = activeLayersMap[layerId] ?: continue
                
                // Get pre-allocated node and resolve properties
                val node = obtainNode()
                CompositionResolver.resolve(
                    clip = clip,
                    currentTime = context.currentTime,
                    layerOrder = compLayer.layerOrder,
                    isProxyMode = context.isProxyMode,
                    targetNode = node
                )

                compLayer.nodes.add(node)
            }
        }

        // Apply and resolve transitions for each layer
        for (l in layers) {
            val compLayer = activeLayersMap[l.id] ?: continue
            val transition = TransitionEngine.getInstance().getTransitionAtTime(l.id, context.currentTime)
            if (transition != null) {
                var outgoingNode = compLayer.nodes.find { it.clipId == transition.outgoingClipId }
                var incomingNode = compLayer.nodes.find { it.clipId == transition.incomingClipId }

                if (outgoingNode == null && transition.outgoingClipId != null) {
                    val clip = timelineEngine.getClip(transition.outgoingClipId!!)
                    if (clip != null) {
                        outgoingNode = obtainNode()
                        CompositionResolver.resolve(
                            clip = clip,
                            currentTime = context.currentTime,
                            layerOrder = compLayer.layerOrder,
                            isProxyMode = context.isProxyMode,
                            targetNode = outgoingNode
                        )
                        compLayer.nodes.add(outgoingNode)
                    }
                }

                if (incomingNode == null && transition.incomingClipId != null) {
                    val clip = timelineEngine.getClip(transition.incomingClipId!!)
                    if (clip != null) {
                        incomingNode = obtainNode()
                        CompositionResolver.resolve(
                            clip = clip,
                            currentTime = context.currentTime,
                            layerOrder = compLayer.layerOrder,
                            isProxyMode = context.isProxyMode,
                            targetNode = incomingNode
                        )
                        compLayer.nodes.add(incomingNode)
                    }
                }

                TransitionResolver.resolve(
                    transition = transition,
                    currentTime = context.currentTime,
                    viewportWidth = context.viewportWidth,
                    viewportHeight = context.viewportHeight,
                    outgoingNode = outgoingNode,
                    incomingNode = incomingNode
                )
            }
        }


        // Assemble active layers into our Composition Graph and sort them
        val graph = obtainGraph()
        for (compLayer in activeLayersMap.values) {
            if (compLayer.nodes.isNotEmpty()) {
                graph.addLayer(compLayer)
            } else {
                // Return unused layers directly to pool
                compLayer.reset()
                layerPool.offer(compLayer)
            }
        }

        // Sort layers using LayerSorter based on track order
        LayerSorter.sortLayers(graph.layers)

        // Compile nodes into categorized output streams
        for (layer in graph.layers) {
            // Sort nodes within the same track in-place (ascending order)
            LayerSorter.sortNodes(layer.nodes)

            val trackIsHidden = layer.isHidden
            val trackIsMuted = layer.isMuted

            for (node in layer.nodes) {
                // Copy values into another obtained node to maintain isolation in outputs
                val outNode = obtainNode()
                outNode.copyFrom(node)

                when (node.type) {
                    ClipType.VIDEO, ClipType.IMAGE -> {
                        if (!trackIsHidden) {
                            activeOutput.videoNodes.add(outNode)
                        } else {
                            releaseNode(outNode)
                        }
                    }
                    ClipType.AUDIO -> {
                        if (!trackIsMuted) {
                            activeOutput.audioNodes.add(outNode)
                        } else {
                            releaseNode(outNode)
                        }
                    }
                    ClipType.TEXT -> {
                        if (!trackIsHidden) {
                            activeOutput.textNodes.add(outNode)
                        } else {
                            releaseNode(outNode)
                        }
                    }
                }
            }
        }

        // Release graph resources back to pool for future ticks
        releaseGraph(graph)

        return activeOutput
    }
    
    /**
     * Reclaims heap memory on system trim/stop.
     */
    fun clearPools() {
        nodePool.clear()
        layerPool.clear()
        graphPool.clear()
        activeOutput.reset()
    }
}
