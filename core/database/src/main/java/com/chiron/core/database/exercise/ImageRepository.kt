package com.chiron.core.database.exercise

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Manages copying and deleting exercise images in app-managed internal storage.
 */
class ImageRepository(private val context: Context) {

    /**
     * Copy an image from [sourceUri] into app-managed storage under `files/images/exercises/`.
     * Returns the internal file URI string, or `null` on failure.
     */
    fun copyImageToStorage(sourceUri: Uri, exerciseId: Long): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val imagesDir = File(context.filesDir, "images/exercises")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val extension = context.contentResolver.getType(sourceUri)
                ?.substringAfter("/") ?: "jpg"
            val fileName = "${exerciseId}_${System.currentTimeMillis()}.$extension"
            val destFile = File(imagesDir, fileName)

            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Delete an image file from app storage.
     * Returns `true` if deleted, `false` otherwise.
     */
    fun deleteImage(imageUri: String): Boolean {
        return try {
            val file = File(Uri.parse(imageUri).path ?: return false)
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
