package xx.snapsort

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DuplicatesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, bytes: ByteArray, modified: Long = 0L): File =
        File(tmp.root, path).apply {
            parentFile!!.mkdirs()
            writeBytes(bytes)
            if (modified > 0) setLastModified(modified)
        }

    private fun bytes(size: Int, seed: Int) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private fun find(minSize: Long = 1): List<Duplicates.Group> {
        val files = ImageScanner.scan(tmp.root, ImageScanner.Progress(), sortFolders = true) {}.map { it.file }
        return runBlocking { Duplicates.confirm(Duplicates.candidates(files, minSize)) }
    }

    @Test
    fun findsRenamedCopiesMostCopiesFirst() {
        val big = bytes(300_000, 1)
        file("DCIM/IMG_1.jpg", big)
        file("Download/IMG-WA0001.jpg", big)
        file("Download/other (1).jpg", big)
        val small = bytes(10, 2)
        file("a.png", small)
        file("b/a.png", small)
        file("lonely.jpg", bytes(10, 3))

        val groups = find()
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].copies.size)
        assertEquals(2, groups[1].copies.size)
    }

    @Test
    fun sameEdgesDifferentMiddleAreNotCopies() {
        // Longer than both edges, so only the full hash can tell them apart.
        val a = bytes(400_000, 5)
        val b = a.copyOf().also { it[200_000] = (it[200_000] + 1).toByte() }
        file("a.jpg", a)
        file("b.jpg", b)
        assertTrue(find().isEmpty())
    }

    @Test
    fun smallFilesAndHiddenFoldersStayOut() {
        val data = bytes(1000, 7)
        file("x.jpg", data)
        file("y.jpg", data)
        file(".Trash/z.jpg", data)
        assertTrue(find(minSize = Duplicates.MIN_SIZE).isEmpty())
        val groups = find()
        assertEquals(setOf("x.jpg", "y.jpg"), groups.single().copies.map { it.name }.toSet())
    }

    @Test
    fun copyMarks() {
        listOf("IMG_1 (1).jpg", "report - Copy.pdf", "report_copy2.pdf", "Copy of notes.txt",
            "Копия отчёт.doc", "отчёт - копия.doc", "звіт копія 3.doc").forEach {
            assertTrue(it, Duplicates.looksLikeCopy(it))
        }
        listOf("IMG_1.jpg", "copyright.txt", "photo(1)x.jpg", "scopy.bin", "(1).jpg.bak").forEach {
            assertFalse(it, Duplicates.looksLikeCopy(it))
        }
    }

    @Test
    fun keeperPrefersPlainNameThenOldestThenShortestPath() {
        val data = bytes(100, 9)
        file("long/path/photo (1).jpg", data, modified = 1_000_000_000_000)
        file("long/path/photo.jpg", data, modified = 1_700_000_000_000)
        file("p/photo.jpg", data, modified = 1_700_000_000_000)
        val group = find().single()
        assertEquals(tmp.root.absolutePath + "/p/photo.jpg", Duplicates.keeper(group).file.path)

        file("p/photo.jpg", data, modified = 1_800_000_000_000)
        val regrouped = find().single()
        assertEquals(tmp.root.absolutePath + "/long/path/photo.jpg", Duplicates.keeper(regrouped).file.path)
    }

    @Test
    fun keeperPrefersACopyAlreadySorted() {
        val data = bytes(100, 11)
        file("DCIM/photo.jpg", data, modified = 1_000_000_000_000)
        file("DCIM/-Best/photo (1).jpg", data, modified = 1_800_000_000_000)
        assertEquals("photo (1).jpg", Duplicates.keeper(find().single()).name)
    }
}
