package com.litecut.app.timeline

class CompositionGraph {
    val layers = ArrayList<CompositionLayer>()
    
    // Graph representation of compound clips, nested clips, or complex effects routes
    private val nodeDependencies = HashMap<String, ArrayList<String>>()

    fun addLayer(layer: CompositionLayer) {
        layers.add(layer)
    }

    fun addDependency(parentNodeId: String, childNodeId: String) {
        val list = nodeDependencies.getOrPut(parentNodeId) { ArrayList() }
        if (!list.contains(childNodeId)) {
            list.add(childNodeId)
        }
    }

    fun getDependencies(nodeId: String): List<String>? {
        return nodeDependencies[nodeId]
    }

    fun reset() {
        layers.clear()
        nodeDependencies.clear()
    }
}
