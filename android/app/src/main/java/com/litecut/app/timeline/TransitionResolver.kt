package com.litecut.app.timeline

import kotlin.math.max

object TransitionResolver {
    /**
     * Resolves the spatial, opacity, and crop metadata for the outgoing and incoming clips during a transition.
     * Modifies the supplied CompositionNodes in-place to avoid dynamic memory allocations during playback.
     */
    fun resolve(
        transition: Transition,
        currentTime: Double,
        viewportWidth: Int,
        viewportHeight: Int,
        outgoingNode: CompositionNode?,
        incomingNode: CompositionNode?
    ) {
        val progress = TransitionEvaluator.evaluateProgress(transition, currentTime).toFloat()
        
        when (transition.type) {
            TransitionType.CUT -> {
                if (progress < 0.5f) {
                    outgoingNode?.opacity = 1.0f
                    incomingNode?.opacity = 0.0f
                } else {
                    outgoingNode?.opacity = 0.0f
                    incomingNode?.opacity = 1.0f
                }
            }
            TransitionType.CROSS_DISSOLVE -> {
                outgoingNode?.let { it.opacity *= (1.0f - progress) }
                incomingNode?.let { it.opacity *= progress }
            }
            TransitionType.FADE -> {
                if (progress < 0.5f) {
                    outgoingNode?.let { it.opacity *= (1.0f - progress * 2.0f) }
                    incomingNode?.opacity = 0.0f
                } else {
                    outgoingNode?.opacity = 0.0f
                    incomingNode?.let { it.opacity *= ((progress - 0.5f) * 2.0f) }
                }
            }
            TransitionType.DIP_TO_BLACK -> {
                // Dip to solid black color. In the composition pipeline, 
                // this acts similarly to a fade out then fade in with background showing.
                if (progress < 0.5f) {
                    outgoingNode?.let { it.opacity *= (1.0f - progress * 2.0f) }
                    incomingNode?.opacity = 0.0f
                } else {
                    outgoingNode?.opacity = 0.0f
                    incomingNode?.let { it.opacity *= ((progress - 0.5f) * 2.0f) }
                }
            }
            TransitionType.DIP_TO_WHITE -> {
                // Dip to white is represented by increasing solid white overlay or blending.
                // We'll mark the nodes' brightness properties if available, or fade opacity down
                if (progress < 0.5f) {
                    outgoingNode?.let { it.opacity *= (1.0f - progress * 2.0f) }
                    incomingNode?.opacity = 0.0f
                } else {
                    outgoingNode?.opacity = 0.0f
                    incomingNode?.let { it.opacity *= ((progress - 0.5f) * 2.0f) }
                }
                // Transition metadata passed to renderer to draw solid white overlay with opacity
                val overlayOpacity = if (progress < 0.5f) progress * 2.0f else (1.0f - progress) * 2.0f
                transition.additionalParams["current_white_overlay"] = overlayOpacity
            }
            TransitionType.WIPE -> {
                // Wipe transition left-to-right
                outgoingNode?.let {
                    it.cropRight = progress
                }
                incomingNode?.let {
                    it.cropLeft = 1.0f - progress
                }
            }
            TransitionType.PUSH -> {
                // Horizontal push left-to-right
                val shiftX = progress * viewportWidth
                outgoingNode?.let {
                    it.translationX = -shiftX
                }
                incomingNode?.let {
                    it.translationX = viewportWidth - shiftX
                }
            }
            TransitionType.SLIDE -> {
                // Slide incoming over outgoing
                incomingNode?.let {
                    it.translationX = viewportWidth * (1.0f - progress)
                }
            }
            TransitionType.ZOOM -> {
                // Zoom out outgoing, zoom in incoming
                outgoingNode?.let {
                    it.scaleX *= (1.0f - progress)
                    it.scaleY *= (1.0f - progress)
                    it.opacity *= (1.0f - progress)
                }
                incomingNode?.let {
                    it.scaleX *= progress
                    it.scaleY *= progress
                    it.opacity *= progress
                }
            }
            TransitionType.BLUR -> {
                // Blur out outgoing, blur in incoming
                val maxBlurRadius = 50.0f
                outgoingNode?.let {
                    it.opacity *= (1.0f - progress)
                    it.effectId = "BLUR"
                    transition.additionalParams["outgoing_blur_radius"] = (1.0f - progress) * maxBlurRadius
                }
                incomingNode?.let {
                    it.opacity *= progress
                    it.effectId = "BLUR"
                    transition.additionalParams["incoming_blur_radius"] = progress * maxBlurRadius
                }
            }
            TransitionType.CUSTOM -> {
                // Custom transition shader metadata evaluation for future extensibility
                val customEval = transition.additionalParams["evaluator_key"] as? String
                transition.additionalParams["resolved_progress"] = progress
            }
        }
    }
}
