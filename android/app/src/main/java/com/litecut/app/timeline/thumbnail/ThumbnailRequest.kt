package com.litecut.app.timeline.thumbnail

import android.graphics.Bitmap

data class ThumbnailRequest(
    val clipId: String,
    val src: String,
    val timeOffsetSeconds: Double,
    val width: Int,
    val height: Int,
    var priority: Int = 0,
    val onComplete: (Bitmap) -> Unit
) : Comparable<ThumbnailRequest> {
    
    // Unique key for matching requests and caching
    val key: String = "$clipId@$timeOffsetSeconds"

    override fun compareTo(other: ThumbnailRequest): Int {
        // Compare by priority descending (higher values run first)
        return other.priority.compareTo(this.priority)
    }
}
