package com.chiron.core.database.transfer

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Replace the same filenames, retaining originals until the database import commits. */
class BackupFileTransaction(private val rollbackDirectory: File) {
    private data class Change(val destination: File, val original: File?)
    private val changes = mutableListOf<Change>()

    fun install(source: File, destination: File) {
        check(destination.parentFile?.let { it.mkdirs() || it.isDirectory } == true) {
            "Cannot create backup asset directory"
        }
        val original = if (destination.exists()) {
            check(destination.isFile) { "Backup asset destination is not a file" }
            check(rollbackDirectory.mkdirs() || rollbackDirectory.isDirectory)
            File(rollbackDirectory, changes.size.toString()).also { destination.copyTo(it) }
        } else null
        changes.add(Change(destination, original))
        replace(source, destination)
    }

    fun rollback() {
        var failure: Exception? = null
        changes.asReversed().forEach { (destination, original) ->
            try {
                if (original != null) replace(original, destination)
                else check(!destination.exists() || destination.delete()) { "Cannot remove imported asset" }
            } catch (error: Exception) {
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun replace(source: File, destination: File) {
        val staged = File.createTempFile(".chiron_restore_", ".tmp", destination.parentFile)
        try {
            source.copyTo(staged, overwrite = true)
            try {
                Files.move(staged.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged.toPath(), destination.toPath(), REPLACE_EXISTING)
            }
        } finally {
            staged.delete()
        }
    }
}
