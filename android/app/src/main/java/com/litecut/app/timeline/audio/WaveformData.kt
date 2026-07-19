package com.litecut.app.timeline.audio

/**
 * Immutable production-grade waveform data container.
 * Pre-downsampled and cached across multiple Levels of Detail (LOD) for responsive rendering.
 * Designed with extensible metadata for future compatibility with volume automation, beat detection, and AI analysis.
 */
class WaveformData(
    val clipId: String,
    val isStereo: Boolean,
    val durationSeconds: Double,
    val sampleRate: Int,
    val channels: Int,

    // LOD 0 (fully zoomed out): ~150 points
    val lod0Left: FloatArray,
    val lod0Right: FloatArray?,

    // LOD 1 (low detail): ~450 points
    val lod1Left: FloatArray,
    val lod1Right: FloatArray?,

    // LOD 2 (medium detail): ~1200 points
    val lod2Left: FloatArray,
    val lod2Right: FloatArray?,

    // LOD 3 (high detail): ~3600 points
    val lod3Left: FloatArray,
    val lod3Right: FloatArray?,

    // LOD 4 (maximum detail / sample accurate base): ~10800 points
    val lod4Left: FloatArray,
    val lod4Right: FloatArray?,

    // Extensible properties for future features (Beat detection, voice, volume, etc.)
    val beatMarkers: FloatArray = FloatArray(0),
    val volumeEnvelope: FloatArray = FloatArray(0),
    val isSilence: Boolean = false,
    val avgLoudnessDb: Float = 0f
) {
    fun getLeftArray(lod: Int): FloatArray {
        return when (lod) {
            0 -> lod0Left
            1 -> lod1Left
            2 -> lod2Left
            3 -> lod3Left
            else -> lod4Left ?: lod3Left
        }
    }

    fun getRightArray(lod: Int): FloatArray? {
        return when (lod) {
            0 -> lod0Right
            1 -> lod1Right
            2 -> lod2Right
            3 -> lod3Right
            else -> lod4Right ?: lod3Right
        }
    }

    /**
     * Compute approximate memory consumption in bytes.
     */
    fun sizeBytes(): Long {
        var totalFloats = 0L
        totalFloats += lod0Left.size + (lod0Right?.size ?: 0)
        totalFloats += lod1Left.size + (lod1Right?.size ?: 0)
        totalFloats += lod2Left.size + (lod2Right?.size ?: 0)
        totalFloats += lod3Left.size + (lod3Right?.size ?: 0)
        totalFloats += (lod4Left?.size ?: 0) + (lod4Right?.size ?: 0)
        totalFloats += beatMarkers.size
        totalFloats += volumeEnvelope.size
        return totalFloats * 4L
    }
}
