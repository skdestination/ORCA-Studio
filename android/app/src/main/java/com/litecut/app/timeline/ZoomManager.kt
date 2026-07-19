package com.litecut.app.timeline

class ZoomManager {
    var minZoom: Double = 0.1
    var maxZoom: Double = 10.0
    var basePixelsPerSecond: Double = 100.0
    var currentZoom: Double = 1.0
        private set

    fun setZoom(level: Double) {
        currentZoom = level.coerceIn(minZoom, maxZoom)
    }

    fun getPixelsPerSecond(): Double {
        return basePixelsPerSecond * currentZoom
    }

    /**
     * Determines Level of Detail (LOD) for the UI based on zoom level.
     * 0 -> Low LOD: simple rendering, no waveforms, no text previews, no multi-thumbnails.
     * 1 -> Medium LOD: partial previews, sparse thumbnails.
     * 2 -> High LOD: detailed waveforms and continuous thumbnails.
     */
    fun getLevelOfDetail(): Int {
        return when {
            currentZoom < 0.3 -> 0
            currentZoom < 0.8 -> 1
            else -> 2
        }
    }
}
