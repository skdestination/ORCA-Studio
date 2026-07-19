package com.litecut.app.timeline

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class AssetDatabase {

    private val indexer = AssetIndexer()
    private val dbFileName = "orca_media_database.json"

    companion object {
        private const val TAG = "AssetDatabase"
    }

    /**
     * Loads the indexed database file from persistent local storage.
     */
    @Synchronized
    fun load(context: Context) {
        val file = File(context.filesDir, dbFileName)
        if (!file.exists()) {
            Log.i(TAG, "No existing media database file found. Starting fresh.")
            return
        }

        try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val array = JSONArray(jsonStr)
            
            indexer.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val entry = AssetEntry.fromJSONObject(obj)
                indexer.index(entry)
            }
            Log.i(TAG, "Successfully loaded ${array.length()} asset entries from disk.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load media database from disk", e)
        }
    }

    /**
     * Saves the indexed database file to persistent local storage.
     */
    @Synchronized
    fun save(context: Context) {
        val file = File(context.filesDir, dbFileName)
        try {
            val array = JSONArray()
            indexer.getAll().forEach { entry ->
                array.put(entry.toJSONObject())
            }

            file.parentFile?.mkdirs()
            FileOutputStream(file).use { fos ->
                fos.write(array.toString().toByteArray(Charsets.UTF_8))
            }
            Log.d(TAG, "Successfully saved media database to disk: ${indexer.getAll().size} assets.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media database to disk", e)
        }
    }

    @Synchronized
    fun put(entry: AssetEntry) {
        indexer.index(entry)
    }

    @Synchronized
    fun get(id: String): AssetEntry? = indexer.get(id)

    @Synchronized
    fun remove(id: String): AssetEntry? {
        val entry = indexer.get(id)
        if (entry != null) {
            indexer.deindex(id)
        }
        return entry
    }

    @Synchronized
    fun getAll(): List<AssetEntry> = indexer.getAll()

    @Synchronized
    fun clear() {
        indexer.clear()
    }

    // --- Search Delegation (O(log n) where possible) ---

    fun findByName(name: String): List<AssetEntry> = indexer.findByName(name)
    fun findByType(type: AssetType): List<AssetEntry> = indexer.findByType(type)
    fun findByTag(tag: String): List<AssetEntry> = indexer.findByTag(tag)
    fun findByCollection(col: String): List<AssetEntry> = indexer.findByCollection(col)
    fun findFavorites(): List<AssetEntry> = indexer.findFavorites()
    fun findByColorLabel(colorLabel: Int): List<AssetEntry> = indexer.findByColorLabel(colorLabel)
    fun findByDurationRange(minMs: Long, maxMs: Long): List<AssetEntry> = indexer.findByDurationRange(minMs, maxMs)
    fun findByFpsRange(minFps: Float, maxFps: Float): List<AssetEntry> = indexer.findByFpsRange(minFps, maxFps)
    fun findByImportDateRange(startMs: Long, endMs: Long): List<AssetEntry> = indexer.findByImportDateRange(startMs, endMs)
}
