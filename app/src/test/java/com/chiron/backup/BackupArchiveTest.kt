package com.chiron.backup

import com.chiron.core.database.transfer.BackupArchive
import com.chiron.core.database.transfer.BackupFileTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun roundTripPreservesDatabaseSettingsAndAssetFilenames() {
        val database = temporary.newFile("source.db").apply { writeText("database bytes") }
        val image = temporary.newFile("12_123456.jpg").apply { writeText("image bytes") }
        val archive = ByteArrayOutputStream()
        BackupArchive.write(
            archive, database, """{"version":1}""",
            mapOf("custom.svg" to { ByteArrayInputStream("custom icon".toByteArray()) }),
            listOf(image)
        )
        val contents = BackupArchive.extract(archive.toByteArray().inputStream(), temporary.newFolder("staged"))
        assertEquals("database bytes", contents.database.readText())
        assertEquals("""{"version":1}""", contents.settings?.readText())
        assertEquals("custom.svg", contents.icons.single().name)
        assertEquals("custom icon", contents.icons.single().readText())
        assertEquals(image.name, contents.images.single().name)
        assertEquals("image bytes", contents.images.single().readText())
    }

    @Test
    fun legacyArchiveWithoutSettingsStillExtracts() {
        val contents = BackupArchive.extract(
            zip("chiron_database" to "db", "icons/old.svg" to "icon").inputStream(),
            temporary.newFolder("legacy")
        )
        assertEquals("db", contents.database.readText())
        assertEquals(null, contents.settings)
    }

    @Test
    fun rejectsTraversalAndAbsolutePathsBeforeWriting() {
        listOf("images/../../escape.jpg", "/images/escape.jpg", "icons\\escape.svg", "images/C:/bad").forEachIndexed { index, path ->
            assertThrows(IllegalArgumentException::class.java) {
                BackupArchive.extract(
                    zip("chiron_database.db" to "db", path to "bad").inputStream(),
                    temporary.newFolder("unsafe$index")
                )
            }
        }
        assertFalse(File(temporary.root, "escape.jpg").exists())
    }

    @Test
    fun rejectsAmbiguousOrMissingDatabase() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.extract(
                zip("a.db" to "first", "b.db" to "second").inputStream(),
                temporary.newFolder("multiple")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchive.extract(zip("icons/a.svg" to "icon").inputStream(), temporary.newFolder("missing"))
        }
    }

    @Test
    fun installingOverwritesSameAddressAndRollbackRestoresOriginal() {
        val original = temporary.newFile("same_name.jpg").apply { writeText("original") }
        val imported = temporary.newFile("imported.jpg").apply { writeText("replacement") }
        val fresh = File(temporary.root, "new.svg")
        val files = BackupFileTransaction(File(temporary.root, "rollback"))
        files.install(imported, original)
        files.install(imported, fresh)
        assertEquals("replacement", original.readText())
        assertEquals("replacement", fresh.readText())
        files.rollback()
        assertEquals("original", original.readText())
        assertFalse(fresh.exists())
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
