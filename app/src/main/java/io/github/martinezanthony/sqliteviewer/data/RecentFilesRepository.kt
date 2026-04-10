package io.github.martinezanthony.sqliteviewer.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/**
 * handles persistence of recently opened database URIs
 * using SharedPreferences with JSON serialization.
 */
class RecentFilesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): MutableList<String> {
        val saved = prefs.getString(KEY_URI_PATHS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(saved)
            MutableList(arr.length()) { arr.optString(it, null) }
                .filterNotNull()
                .filter { it.isNotEmpty() }
                .toMutableList()
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    fun save(uris: List<String>) {
        val json = JSONArray().apply { uris.forEach { put(it) } }
        prefs.edit { putString(KEY_URI_PATHS, json.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "recent_files_prefs"
        private const val KEY_URI_PATHS = "uri_paths"
    }
}