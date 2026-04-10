package io.github.martinezanthony.sqliteviewer.utils

import android.content.ContentResolver
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * utility functions for SQLite file validation and cache management
 */
object DatabaseUtils {

    /**
     * returns true if the URI points to a valid SQLite 3 file
     * by inspecting the first 16 bytes of its header.
     */
    fun isSqliteDatabase(contentResolver: ContentResolver, uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(16)
                val bytesRead = input.read(header)
                bytesRead >= 16 && String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * copies the database from uri into the app's cache directory,
     * validates it can be opened, and returns the local file path.
     * Throws an exception if the file is invalid or unreadable
     */
    fun copyToCache(contentResolver: ContentResolver, cacheDir: File, uri: Uri, fileName: String): String {
        val safeName = "${System.currentTimeMillis()}_$fileName"
        val cachedFile = File(cacheDir, safeName)

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cachedFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

        try {
            val db = SQLiteDatabase.openDatabase(
                cachedFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' LIMIT 1", null
            ).use {}
            db.close()
        } catch (e: Exception) {
            cachedFile.delete()
            throw e
        }

        return cachedFile.absolutePath
    }

    /** deletes all files within the cache directory */
    fun clearCache(cacheDir: File) {
        cacheDir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }
}