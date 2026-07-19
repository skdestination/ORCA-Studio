package com.litecut.app.timeline

class CompositionOutput {
    // Retain capacity to avoid resizing array allocations
    val videoNodes = ArrayList<CompositionNode>()
    val audioNodes = ArrayList<CompositionNode>()
    val textNodes = ArrayList<CompositionNode>()
    
    // Future capability expansion: layers processing effects globally
    val activeAdjustmentNodes = ArrayList<CompositionNode>()
    
    var timeSeconds: Double = 0.0
    var isProxyMode: Boolean = false

    fun reset() {
        videoNodes.clear()
        audioNodes.clear()
        textNodes.clear()
        activeAdjustmentNodes.clear()
        timeSeconds = 0.0
        isProxyMode = false
    }
}
