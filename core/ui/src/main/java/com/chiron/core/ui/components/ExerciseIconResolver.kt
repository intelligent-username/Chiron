package com.chiron.core.ui.components

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Resolves exercise icon identifiers and filenames to Coil-loadable URIs or asset URLs.
 */
object ExerciseIconResolver {

    private val ICON_LOOKUP: Map<String, String> by lazy {
        val map = HashMap<String, String>(AVAILABLE_ICONS.size * 4)
        for (icon in AVAILABLE_ICONS) {
            val nameLower = icon.name.lowercase()
            val fileLower = icon.fileName.lowercase()
            val nameHyphen = nameLower.replace('_', '-')
            val fileNoExt = fileLower.removeSuffix(".svg")
            val fileHyphen = fileNoExt.replace('_', '-')

            map[nameLower] = icon.fileName
            map[nameHyphen] = icon.fileName
            map[fileLower] = icon.fileName
            map[fileNoExt] = icon.fileName
            map["$nameLower.svg"] = icon.fileName
            map["$nameHyphen.svg"] = icon.fileName
            map["$fileHyphen.svg"] = icon.fileName
        }
        map
    }

    private val customUrlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Invalidate custom icon file cache when new files are saved or deleted. */
    fun invalidateCustomCache() {
        customUrlCache.clear()
    }

    /**
     * Resolves an exercise icon identifier or file name to the corresponding asset SVG file name.
     * Uses O(1) hash map lookup.
     */
    fun resolveFileName(iconName: String?): String {
        if (iconName.isNullOrBlank()) return "dumbell.svg"
        val clean = iconName.trim().lowercase()
        return ICON_LOOKUP[clean]
            ?: ICON_LOOKUP[clean.removeSuffix(".svg").replace('_', '-')]
            ?: "dumbell.svg"
    }

    /**
     * Returns an image URL suitable for Coil. Checks custom user-imported icons in `filesDir/icons`
     * and custom exercise images in `filesDir/images/exercises/` before falling back to bundled assets.
     * Results are cached in-memory to prevent main-thread disk I/O during list scrolling.
     */
    fun getIconUrl(iconName: String?, context: Context? = null): String {
        if (iconName.isNullOrBlank()) return "file:///android_asset/icons/dumbell.svg"

        if (context != null) {
            val cached = customUrlCache[iconName]
            if (cached != null) {
                if (cached.isNotEmpty()) return cached
            } else {
                var foundUri: String? = null
                val iconsDir = File(context.filesDir, "icons")
                val directFile = File(iconsDir, iconName)
                if (directFile.exists() && directFile.isFile) {
                    foundUri = Uri.fromFile(directFile).toString()
                } else {
                    val svgName = if (iconName.endsWith(".svg", ignoreCase = true)) iconName else "$iconName.svg"
                    val svgFile = File(iconsDir, svgName)
                    if (svgFile.exists() && svgFile.isFile) {
                        foundUri = Uri.fromFile(svgFile).toString()
                    } else {
                        val imagesDir = File(context.filesDir, "images/exercises")
                        val imageFile = File(imagesDir, iconName)
                        if (imageFile.exists() && imageFile.isFile) {
                            foundUri = Uri.fromFile(imageFile).toString()
                        }
                    }
                }
                customUrlCache[iconName] = foundUri ?: ""
                if (foundUri != null) return foundUri
            }
        }

        val resolved = resolveFileName(iconName)
        return "file:///android_asset/icons/$resolved"
    }
}
