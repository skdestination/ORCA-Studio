package com.litecut.app.timeline.resources

data class CacheEntry<V>(
    val key: String,
    val value: V,
    val sizeBytes: Long,
    val policy: CachePolicy = CachePolicy.LRU,
    val createdAt: Long = System.currentTimeMillis()
) {
    var lastAccessedAt: Long = createdAt
    var pinCount: Int = 0

    fun pin() {
        pinCount++
    }

    fun unpin() {
        if (pinCount > 0) pinCount--
    }

    val isPinned: Boolean
        get() = pinCount > 0 || policy == CachePolicy.PINNED || policy == CachePolicy.PERSISTENT
}
