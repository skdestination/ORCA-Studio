package com.litecut.app.timeline

class RenderGraph {
    private val passes = ArrayList<RenderPass>()

    fun addPass(pass: RenderPass) {
        passes.add(pass)
    }

    fun getPasses(): List<RenderPass> {
        return passes
    }

    /**
     * Executes the compiled render graph sequentially.
     */
    fun execute(context: RenderContext, shaderManager: ShaderManager, stats: RenderStatistics) {
        stats.resetDrawCalls()
        for (pass in passes) {
            pass.execute(context, shaderManager, stats)
        }
    }

    fun clear() {
        for (pass in passes) {
            pass.clear()
        }
        passes.clear()
    }

    companion object {
        /**
         * Compiles CompositionOutput into an executable RenderGraph based on active track assets.
         */
        fun compile(
            output: CompositionOutput,
            context: RenderContext,
            onscreenTarget: RenderTarget,
            frameBufferPool: FrameBufferPool,
            textureManager: TextureManager
        ): RenderGraph {
            val graph = RenderGraph()

            // 1. CLEAR PASS: Erase the background
            val clearPass = RenderPass("ClearPass", onscreenTarget)
            clearPass.addNode(RenderNode("ClearNode", RenderNodeType.CLEAR))
            graph.addPass(clearPass)

            // 2. PRIMARY PASS: Render all layers/clips
            val mainPass = RenderPass("MainCompositePass", onscreenTarget)

            // Combine video, image, and text composition nodes
            val sortedNodes = ArrayList<CompositionNode>()
            sortedNodes.addAll(output.videoNodes)
            sortedNodes.addAll(output.textNodes)
            
            // Sort by layer index to respect track layering
            sortedNodes.sortBy { it.layerOrder }

            for (compNode in sortedNodes) {
                val nodeType = when (compNode.type) {
                    ClipType.IMAGE -> RenderNodeType.IMAGE_TEXTURE
                    ClipType.TEXT -> RenderNodeType.TEXT_GRAVITY
                    else -> RenderNodeType.VIDEO_DECODER_FRAME
                }

                val renderNode = RenderNode(compNode.id, nodeType).apply {
                    opacity = compNode.opacity
                    translationX = compNode.translationX
                    translationY = compNode.translationY
                    scaleX = compNode.scaleX
                    scaleY = compNode.scaleY
                    rotation = compNode.rotation
                    cropLeft = compNode.cropLeft
                    cropTop = compNode.cropTop
                    cropRight = compNode.cropRight
                    cropBottom = compNode.cropBottom
                    
                    // Retrieve associated GPU texture
                    // (Falls back to default zero texture if loading is not completed)
                    inputTextureId = compNode.additionalProperties["gl_texture_id"] as? Int ?: 0
                }

                // If there's an active transition on this track, we can composite blending offscreen
                val transitionTypeStr = compNode.transitionType
                if (transitionTypeStr != null) {
                    val transitionType = TransitionType.valueOf(transitionTypeStr)
                    
                    // Allocate temporary offscreen buffer for blending if we have both textures
                    val fbo = frameBufferPool.obtainFrameBuffer(context.viewportWidth, context.viewportHeight)
                    val offscreenTarget = OffscreenTarget(fbo)
                    
                    val blendPass = RenderPass("BlendPass-${compNode.id}", offscreenTarget)
                    val blendNode = RenderNode("BlendNode-${compNode.id}", RenderNodeType.TRANSITION_BLENDER).apply {
                        inputTextureId = compNode.additionalProperties["gl_texture_id"] as? Int ?: 0
                        secondaryTextureId = compNode.additionalProperties["transition_incoming_texture_id"] as? Int ?: 0
                        progress = (compNode.additionalProperties["transition_progress"] as? Number)?.toFloat() ?: 0.0f
                    }
                    
                    blendPass.addNode(blendNode)
                    graph.addPass(blendPass)
                    
                    // Now, draw the blended FBO onto the onscreen display
                    val fboOnscreenNode = RenderNode("FboDraw-${compNode.id}", RenderNodeType.IMAGE_TEXTURE).apply {
                        inputTextureId = fbo.textureId
                        opacity = compNode.opacity
                    }
                    mainPass.addNode(fboOnscreenNode)
                    
                    // Recycle FBO back into pool immediately
                    frameBufferPool.recycleFrameBuffer(fbo)
                } else {
                    mainPass.addNode(renderNode)
                }
            }

            graph.addPass(mainPass)
            return graph
        }
    }
}
