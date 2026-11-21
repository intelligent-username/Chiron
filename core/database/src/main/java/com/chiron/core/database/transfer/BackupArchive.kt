package com.chiron.core.database.transfer

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable archive handling. Extraction only touches a new staging directory. */
object BackupArchive {
    private const val MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000

    data class Contents(
        val database: File,
        val settings: File?,
        val icons: List<File>,
        val images: List<File>
    )

    fun write(
        output: OutputStream,
        database: File,
        settings: String,
        icons: Map<String, () -> InputStream>,
        images: List<File>
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            fun entry(name: String, input: () -> InputStream) {
                validatePath(name)
                zip.putNextEntry(ZipEntry(name))
                input().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            entry("chiron_database.db") { database.inputStream() }
            entry("settings.json") { settings.byteInputStream(Charsets.UTF_8) }
            icons.forEach { (name, open) -> entry("icons/$name", open) }
            images.forEach { file -> entry("images/${file.name}") { file.inputStream() } }
        }
    }

    fun extract(input: InputStream, directory: File): Contents {
        check(directory.mkdirs() || directory.isDirectory) { "Cannot create backup staging directory" }
        var database: File? = null
        var settings: File? = null
        val icons = mutableListOf<File>()
        val images = mutableListOf<File>()
        val seen = mutableSetOf<String>()
        var totalBytes = 0L
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (entry != null) {
                require(++count <= MAX_ENTRIES) { "Backup contains too many files" }
                val name = entry.name
                validatePath(name.removeSuffix("/"))
                require(seen.add(name)) { "Duplicate backup entry: $name" }
                if (!entry.isDirectory) {
                    val path = name.split('/')
                    val destination = when {
                        path.size == 1 && (name.endsWith(".db", true) || name == "chiron_database") -> {
                            require(database == null) { "Backup contains multiple databases" }
                            File(directory, "chiron_database.db").also { database = it }
                        }
                        name == "settings.json" -> File(directory, name).also { settings = it }
                        path.size == 2 && path[0] == "icons" ->
                            File(directory, name).also { icons.add(it) }
                        path.size == 2 && path[0] == "images" ->
                            File(directory, name).also { images.add(it) }
                        else -> null // Allow future nonessential metadata, but still bound its size.
                    }
                    destination?.parentFile?.let {
                        check(it.mkdirs() || it.isDirectory) { "Cannot stage backup file" }
                    }
                    val output = destination?.outputStream()
                    try {
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "Backup exceeds the 512 MiB import limit" }
                            output?.write(buffer, 0, read)
                        }
                    } finally {
                        output?.close()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return Contents(
            database = requireNotNull(database) { "Database not found inside backup zip" },
            settings = settings,
            icons = icons,
            images = images
        )
    }

    private fun validatePath(name: String) {
        require(
            name.isNotBlank() && '\\' !in name && ':' !in name &&
                name.split('/').none { it.isEmpty() || it == "." || it == ".." }
        ) { "Unsafe backup entry: $name" }
    }
}
