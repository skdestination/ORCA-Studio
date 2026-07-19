package com.litecut.app.timeline

class SelectionManager {
    private val selectedIds = mutableSetOf<String>()

    fun select(clipId: String) {
        selectedIds.clear()
        selectedIds.add(clipId)
    }

    fun selectMultiple(clipIds: Collection<String>) {
        selectedIds.addAll(clipIds)
    }

    fun toggleSelection(clipId: String) {
        if (selectedIds.contains(clipId)) {
            selectedIds.remove(clipId)
        } else {
            selectedIds.add(clipId)
        }
    }

    fun deselect(clipId: String) {
        selectedIds.remove(clipId)
    }

    fun clearSelection() {
        selectedIds.clear()
    }

    fun getSelection(): Set<String> = selectedIds.toSet()

    fun isSelected(clipId: String): Boolean = selectedIds.contains(clipId)

    fun selectInBox(
        startX: Double,
        endX: Double,
        clips: List<TimelineClip>,
        pixelsPerSecond: Double
    ) {
        selectedIds.clear()
        val minX = minOf(startX, endX)
        val maxX = maxOf(startX, endX)
        
        clips.forEach { clip ->
            val clipStart = clip.startTime * pixelsPerSecond
            val clipEnd = (clip.startTime + clip.duration) * pixelsPerSecond
            // Overlaps selection box horizontally
            if (clipStart < maxX && clipEnd > minX) {
                selectedIds.add(clip.id)
            }
        }
    }
}
