package io.github.martinezanthony.sqliteviewer.utils

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * utility functions for resolving file metadata from URIs
 */
object FileUtils {

    /**
     * returns the display name of the file pointed to by Uri,
     * or null if it cannot be resolved
     */
    fun getDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index == -1) null else cursor.getString(index)
        }
    }
}