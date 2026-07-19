package com.litecut.app.timeline

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class AssetDependencyGraph {

    // Map of Asset ID -> Set of Clip IDs utilizing it
    private val assetToClips = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>()

    // Map of Asset ID -> Set of Project IDs utilizing it
    private val assetToProjects = ConcurrentHashMap<String, CopyOnWriteArraySet<String>>()

    // Map of Clip ID -> Asset ID (Reverse lookup)
    private val clipToAsset = ConcurrentHashMap<String, String>()

    // Map of Clip ID -> Project ID
    private val clipToProject = ConcurrentHashMap<String, String>()

    /**
     * Registers a relationship where a Clip in a specific Project references an Asset.
     */
    fun registerUsage(projectId: String, clipId: String, assetId: String) {
        // Unregister previous if any
        unregisterUsage(clipId)

        clipToAsset[clipId] = assetId
        clipToProject[clipId] = projectId

        assetToClips.getOrPut(assetId) { CopyOnWriteArraySet() }.add(clipId)
        assetToProjects.getOrPut(assetId) { CopyOnWriteArraySet() }.add(projectId)
    }

    /**
     * Unregisters a Clip's usage of any asset.
     */
    fun unregisterUsage(clipId: String) {
        val assetId = clipToAsset.remove(clipId)
        val projectId = clipToProject.remove(clipId)

        if (assetId != null) {
            val clipsSet = assetToClips[assetId]
            clipsSet?.remove(clipId)
            if (clipsSet != null && clipsSet.isEmpty()) {
                assetToClips.remove(assetId)
            }

            // Recalculate projects using this asset
            recalculateProjectDependenciesForAsset(assetId)
        }
    }

    /**
     * Clears all recorded dependencies associated with a particular project ID.
     */
    fun clearProjectUsages(projectId: String) {
        val clipsToRemove = clipToProject.filter { it.value == projectId }.keys
        for (clipId in clipsToRemove) {
            unregisterUsage(clipId)
        }
    }

    /**
     * Returns the list of Clip IDs referencing the given Asset ID.
     */
    fun getClipsUsingAsset(assetId: String): List<String> {
        return assetToClips[assetId]?.toList() ?: emptyList()
    }

    /**
     * Returns the list of Project IDs referencing the given Asset ID.
     */
    fun getProjectsUsingAsset(assetId: String): List<String> {
        return assetToProjects[assetId]?.toList() ?: emptyList()
    }

    /**
     * An asset is considered safe to delete if there are zero clips using it across any project.
     */
    fun isSafeToDelete(assetId: String): Boolean {
        val clips = assetToClips[assetId]
        return clips == null || clips.isEmpty()
    }

    /**
     * Returns the Asset ID associated with a Clip ID.
     */
    fun getAssetForClip(clipId: String): String? {
        return clipToAsset[clipId]
    }

    private fun recalculateProjectDependenciesForAsset(assetId: String) {
        val clips = assetToClips[assetId]
        if (clips == null || clips.isEmpty()) {
            assetToProjects.remove(assetId)
            return
        }

        val projects = CopyOnWriteArraySet<String>()
        for (clipId in clips) {
            clipToProject[clipId]?.let { projects.add(it) }
        }

        if (projects.isEmpty()) {
            assetToProjects.remove(assetId)
        } else {
            assetToProjects[assetId] = projects
        }
    }

    fun clear() {
        assetToClips.clear()
        assetToProjects.clear()
        clipToAsset.clear()
        clipToProject.clear()
    }
}
