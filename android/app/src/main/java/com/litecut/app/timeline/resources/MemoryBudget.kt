package com.litecut.app.timeline.resources

import android.util.Log

class MemoryBudget(val totalMaxMemoryBytes: Long) {

    private val categoryWeights = HashMap<String, Float>()
    private val categoryLimits = HashMap<String, Long>()

    init {
        // Default relative weight shares for ORCA's standard subsystems
        categoryWeights["thumbnail"] = 0.40f // 40% memory weight share
        categoryWeights["waveform"] = 0.15f  // 15% memory weight share
        categoryWeights["bitmappool"] = 0.25f // 25% memory weight share
        categoryWeights["effects_lut"] = 0.10f // 10% memory weight share
        categoryWeights["ai_tracking"] = 0.10f // 10% memory weight share
        recalculateLimits()
    }

    /**
     * Set a custom proportional weight for a specific module category.
     */
    @Synchronized
    fun setCategoryWeight(category: String, weight: Float) {
        if (weight < 0f) return
        categoryWeights[category] = weight
        recalculateLimits()
    }

    /**
     * Retrieve the precise byte budget allocated to a given module.
     */
    @Synchronized
    fun getLimitForCategory(category: String): Long {
        return categoryLimits[category] ?: (totalMaxMemoryBytes * 0.10).toLong() // Fallback to 10%
    }

    private fun recalculateLimits() {
        val totalWeight = categoryWeights.values.sum()
        if (totalWeight <= 0f) return

        categoryLimits.clear()
        for ((category, weight) in categoryWeights) {
            val ratio = weight / totalWeight
            val calculatedLimit = (totalMaxMemoryBytes * ratio).toLong()
            categoryLimits[category] = calculatedLimit
            Log.d("MemoryBudget", "Category: $category assigned budget: ${calculatedLimit / (1024 * 1024)} MB (${(ratio * 100).toInt()}%)")
        }
    }
}
