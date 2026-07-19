package com.litecut.app.timeline

import java.util.Comparator

object LayerSorter {
    // Declared statically to avoid runtime allocations
    private val layerComparator = Comparator<CompositionLayer> { l1, l2 ->
        l1.layerOrder.compareTo(l2.layerOrder)
    }

    private val nodeComparator = Comparator<CompositionNode> { n1, n2 ->
        if (n1.layerOrder != n2.layerOrder) {
            n1.layerOrder.compareTo(n2.layerOrder)
        } else {
            n1.clipId.compareTo(n2.clipId)
        }
    }

    /**
     * Sorts list of composition layers in-place by their track hierarchy order.
     */
    fun sortLayers(layers: ArrayList<CompositionLayer>) {
        layers.sortWith(layerComparator)
    }

    /**
     * Sorts list of composition nodes in-place by their track hierarchy order (ascending).
     */
    fun sortNodes(nodes: ArrayList<CompositionNode>) {
        nodes.sortWith(nodeComparator)
    }
}
