package com.litecut.app.timeline

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.CopyOnWriteArraySet

class AssetIndexer {

    // --- Core Database Storage ---
    private val entries = ConcurrentHashMap<String, AssetEntry>()

    // --- O(log n) Sorted Indexes ---
    private val filenameIndex = ConcurrentSkipListMap<String, CopyOnWriteArraySet<String>>()
    private val durationIndex = ConcurrentSkipListMap<Long, CopyOnWriteArraySet<String>>()
    private val fpsIndex = ConcurrentSkipListMap<Float, CopyOnWriteArraySet<String>>()
    private val importDateIndex = ConcurrentSkipListMap<Long, CopyOnWriteArraySet<String>>()
    private val resolutionIndex = ConcurrentSkipListMap<String, CopyOnWriteArraySet<String>>()

    // --- O(1) Lookup / Dynamic Category Indexes ---
    private val typeIndex = ConcurrentHashMap<AssetType, CopyOnWriteArraySet<String>>()
    private val tagsIndex = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>()
    private val codecIndex = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>()
    private val favoriteIndex = ConcurrentHashMap<Boolean, CopyOnWriteArraySet<String>>()
    private val collectionIndex = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>()
    private val colorLabelIndex = ConcurrentHashMap<Int, CopyOnWriteArraySet<String>>()

    init {
        favoriteIndex[true] = CopyOnWriteArraySet()
        favoriteIndex[false] = CopyOnWriteArraySet()
    }

    /**
     * Registers or updates an asset inside all search indexes.
     */
    @Synchronized
    fun index(entry: AssetEntry) {
        // Remove old indexed values if updating an existing entry
        val previous = entries[entry.id]
        if (previous != null) {
            deindex(previous.id)
        }

        entries[entry.id] = entry

        // 1. Filename Index (Sorted)
        filenameIndex.getOrPut(entry.assetName.lowercase()) { CopyOnWriteArraySet() }.add(entry.id)

        // 2. Type Index
        typeIndex.getOrPut(entry.type) { CopyOnWriteArraySet() }.add(entry.id)

        // 3. Duration Index (Sorted)
        val duration = when (entry.type) {
            AssetType.VIDEO -> entry.videoDurationMs
            AssetType.AUDIO -> entry.audioDurationMs
            else -> 0L
        }
        durationIndex.getOrPut(duration) { CopyOnWriteArraySet() }.add(entry.id)

        // 4. Codec Index
        val codec = when (entry.type) {
            AssetType.VIDEO -> entry.videoCodec
            AssetType.AUDIO -> entry.audioCodec
            else -> "none"
        }
        codecIndex.getOrPut(codec.lowercase()) { CopyOnWriteArraySet() }.add(entry.id)

        // 5. Resolution Index (Sorted)
        val resolution = when (entry.type) {
            AssetType.VIDEO -> "${entry.videoWidth}x${entry.videoHeight}"
            AssetType.IMAGE -> "${entry.imageWidth}x${entry.imageHeight}"
            else -> "0x0"
        }
        resolutionIndex.getOrPut(resolution) { CopyOnWriteArraySet() }.add(entry.id)

        // 6. FPS Index (Sorted)
        val fps = if (entry.type == AssetType.VIDEO) entry.videoFps else 0.0f
        fpsIndex.getOrPut(fps) { CopyOnWriteArraySet() }.add(entry.id)

        // 7. Favorite Index
        favoriteIndex.getOrPut(entry.isFavorite) { CopyOnWriteArraySet() }.add(entry.id)

        // 8. Import Date Index (Sorted)
        importDateIndex.getOrPut(entry.importDateMs) { CopyOnWriteArraySet() }.add(entry.id)

        // 9. Color Label Index
        colorLabelIndex.getOrPut(entry.colorLabel) { CopyOnWriteArraySet() }.add(entry.id)

        // 10. Tags Indexes
        for (tag in entry.tags) {
            tagsIndex.getOrPut(tag.lowercase()) { CopyOnWriteArraySet() }.add(entry.id)
        }

        // 11. Collections Indexes
        for (col in entry.collections) {
            collectionIndex.getOrPut(col.lowercase()) { CopyOnWriteArraySet() }.add(entry.id)
        }
    }

    /**
     * Clears an asset from all memory lookup indexes.
     */
    @Synchronized
    fun deindex(id: String) {
        val entry = entries.remove(id) ?: return

        filenameIndex[entry.assetName.lowercase()]?.remove(id)
        typeIndex[entry.type]?.remove(id)

        val duration = if (entry.type == AssetType.VIDEO) entry.videoDurationMs else entry.audioDurationMs
        durationIndex[duration]?.remove(id)

        val codec = if (entry.type == AssetType.VIDEO) entry.videoCodec else entry.audioCodec
        codecIndex[codec.lowercase()]?.remove(id)

        val resolution = if (entry.type == AssetType.VIDEO) "${entry.videoWidth}x${entry.videoHeight}" else "${entry.imageWidth}x${entry.imageHeight}"
        resolutionIndex[resolution]?.remove(id)

        val fps = if (entry.type == AssetType.VIDEO) entry.videoFps else 0.0f
        fpsIndex[fps]?.remove(id)

        favoriteIndex[true]?.remove(id)
        favoriteIndex[false]?.remove(id)

        importDateIndex[entry.importDateMs]?.remove(id)
        colorLabelIndex[entry.colorLabel]?.remove(id)

        for (tag in entry.tags) {
            tagsIndex[tag.lowercase()]?.remove(id)
        }

        for (col in entry.collections) {
            collectionIndex[col.lowercase()]?.remove(id)
        }
    }

    fun get(id: String): AssetEntry? = entries[id]

    fun getAll(): List<AssetEntry> = entries.values.toList()

    fun clear() {
        entries.clear()
        filenameIndex.clear()
        durationIndex.clear()
        fpsIndex.clear()
        importDateIndex.clear()
        resolutionIndex.clear()
        typeIndex.clear()
        tagsIndex.clear()
        codecIndex.clear()
        favoriteIndex.clear()
        favoriteIndex[true] = CopyOnWriteArraySet()
        favoriteIndex[false] = CopyOnWriteArraySet()
        collectionIndex.clear()
        colorLabelIndex.clear()
    }

    // --- Search Queries O(log n) ---

    fun findById(id: String): AssetEntry? = get(id)

    fun findByName(nameSubstring: String): List<AssetEntry> {
        val lower = nameSubstring.lowercase()
        // O(log n) prefix matching using subMap
        val matchingKeys = filenameIndex.subMap(lower, lower + Character.MAX_VALUE)
        val ids = mutableSetOf<String>()
        matchingKeys.values.forEach { ids.addAll(it) }

        // Also fallback to O(n) exact substring scan if no direct prefix match found
        if (ids.isEmpty()) {
            return entries.values.filter { it.assetName.lowercase().contains(lower) }
        }
        return ids.mapNotNull { entries[it] }
    }

    fun findByType(type: AssetType): List<AssetEntry> {
        val ids = typeIndex[type] ?: return emptyList()
        return ids.mapNotNull { entries[it] }
    }

    fun findByTag(tag: String): List<AssetEntry> {
        val ids = tagsIndex[tag.lowercase()] ?: return emptyList()
        return ids.mapNotNull { entries[it] }
    }

    fun findByCollection(collection: String): List<AssetEntry> {
        val ids = collectionIndex[collection.lowercase()] ?: return emptyList()
        return ids.mapNotNull { entries[it] }
    }

    fun findByCodec(codec: String): List<AssetEntry> {
        val ids = codecIndex[codec.lowercase()] ?: return emptyList()
        return ids.mapNotNull { entries[it] }
    }

    fun findFavorites(): List<AssetEntry> {
        val ids = favoriteIndex[true] ?: return emptyList()
        return ids.mapNotNull { entries[it] }
    }

    fun findByColorLabel(colorLabel: Int): List<AssetEntry> {
        val ids = colorLabelIndex[colorLabel] ?: return emptyList()
        return ids.mapNotNull { entries[it] }
    }

    /**
     * O(log n) range query on Duration.
     */
    fun findByDurationRange(minMs: Long, maxMs: Long): List<AssetEntry> {
        val subMap = durationIndex.subMap(minMs, true, maxMs, true)
        val ids = mutableSetOf<String>()
        subMap.values.forEach { ids.addAll(it) }
        return ids.mapNotNull { entries[it] }
    }

    /**
     * O(log n) range query on FPS.
     */
    fun findByFpsRange(minFps: Float, maxFps: Float): List<AssetEntry> {
        val subMap = fpsIndex.subMap(minFps, true, maxFps, true)
        val ids = mutableSetOf<String>()
        subMap.values.forEach { ids.addAll(it) }
        return ids.mapNotNull { entries[it] }
    }

    /**
     * O(log n) range query on Import Date.
     */
    fun findByImportDateRange(startDateMs: Long, endDateMs: Long): List<AssetEntry> {
        val subMap = importDateIndex.subMap(startDateMs, true, endDateMs, true)
        val ids = mutableSetOf<String>()
        subMap.values.forEach { ids.addAll(it) }
        return ids.mapNotNull { entries[it] }
    }
}
