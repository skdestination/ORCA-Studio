package com.litecut.app.timeline

class CompositionLayer {
    var layerId: String = ""
    var layerOrder: Int = 0
    var isHidden: Boolean = false
    var isMuted: Boolean = false
    
    // Reuse list container to completely prevent allocation churn in high frame rate playback
    val nodes = ArrayList<CompositionNode>()

    fun reset() {
        layerId = ""
        layerOrder = 0
        isHidden = false
        isMuted = false
        nodes.clear()
    }
}
