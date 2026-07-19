package com.litecut.app.timeline.audio

/**
 * Generates and downsamples multi-resolution Levels of Detail (LOD) for the Waveform Engine.
 * Employs a peak-preserving decimation algorithm to ensure transient peaks and beats
 * remain punchy and visually crisp even when fully zoomed out.
 */
object WaveformLOD {

    const val SIZE_LOD4 = 10800 // Maximum detail / base resolution
    const val SIZE_LOD3 = 3600  // High detail
    const val SIZE_LOD2 = 1200  // Medium detail
    const val SIZE_LOD1 = 450   // Low detail
    const val SIZE_LOD0 = 150   // Very low detail

    class MultiLODData(
        val lod0: FloatArray,
        val lod1: FloatArray,
        val lod2: FloatArray,
        val lod3: FloatArray,
        val lod4: FloatArray
    )

    /**
     * Builds all 5 Levels of Detail from a base FloatArray.
     */
    fun buildLODs(basePeaks: FloatArray): MultiLODData {
        // Enforce base size constraint first
        val lod4 = if (basePeaks.size == SIZE_LOD4) {
            basePeaks
        } else {
            downsample(basePeaks, SIZE_LOD4)
        }

        val lod3 = downsample(lod4, SIZE_LOD3)
        val lod2 = downsample(lod4, SIZE_LOD2)
        val lod1 = downsample(lod4, SIZE_LOD1)
        val lod0 = downsample(lod4, SIZE_LOD0)

        return MultiLODData(lod0, lod1, lod2, lod3, lod4)
    }

    /**
     * Slices the source peaks into target size bins, selecting the max peak inside each bin
     * to preserve visual transients (beats/spikes) on compressed resolutions.
     */
    fun downsample(src: FloatArray, targetSize: Int): FloatArray {
        if (src.size <= targetSize) {
            // Upsample or copy directly
            val dest = FloatArray(targetSize)
            val factor = src.size.toFloat() / targetSize
            for (i in 0 until targetSize) {
                val idx = (i * factor).toInt().coerceIn(0, src.size - 1)
                dest[i] = src[idx]
            }
            return dest
        }

        val dest = FloatArray(targetSize)
        val factor = src.size.toFloat() / targetSize

        for (i in 0 until targetSize) {
            val startIdx = (i * factor).toInt().coerceIn(0, src.size - 1)
            val endIdx = ((i + 1) * factor).toInt().coerceIn(0, src.size - 1)

            var maxVal = 0.0f
            for (j in startIdx..endIdx) {
                val absVal = src[j]
                if (absVal > maxVal) {
                    maxVal = absVal
                }
            }
            // Add a small noise floor fallback to keep things looking rich
            dest[i] = maxVal.coerceIn(0.005f, 1.0f)
        }
        return dest
    }
}
