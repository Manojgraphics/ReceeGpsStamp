package com.receegpsstamp.data.capture

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renames a recce's captured photos to "<shopCode>_<shopName>_<n>.jpg" once the shop code is known
 * (at save time). Renames the app-private file AND its device-gallery copy, and returns an
 * old-path -> new-path map so the recce can store the new paths. Best-effort: anything that fails
 * keeps its original name.
 */
@Singleton
class PhotoNamer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Photos numbered 1..N in the given order. Returns old-path -> new-path (unchanged paths included). */
    fun rename(photos: List<String>, shopCode: String, shopName: String): Map<String, String> {
        if (shopCode.isBlank() || photos.isEmpty()) return emptyMap()
        val safeName = shopName.filter { it.isLetterOrDigit() }.take(20).ifBlank { "Shop" }
        val map = LinkedHashMap<String, String>()
        photos.forEachIndexed { i, oldPath ->
            map[oldPath] = renameOne(oldPath, "${shopCode}_${safeName}_${i + 1}") ?: oldPath
        }
        return map
    }

    private fun renameOne(oldPath: String, baseName: String): String? {
        val old = File(oldPath)
        if (!old.exists()) return null
        val dir = old.parentFile ?: return null
        var target = File(dir, "$baseName.jpg")
        var n = 2
        while (target.exists() && target.path != old.path) { target = File(dir, "$baseName-$n.jpg"); n++ }
        if (target.path == old.path) return old.path           // already named correctly
        val oldName = old.name
        if (!old.renameTo(target)) return null
        renameGalleryCopy(oldName, target.name)
        return target.path
    }

    /** Renames the matching device-gallery (MediaStore) copy by display name. App owns it, so allowed. */
    private fun renameGalleryCopy(oldName: String, newName: String) {
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            resolver.query(
                collection, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(oldName), null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, newName) }, null, null)
                }
            }
        } catch (_: Throwable) { }
    }
}
