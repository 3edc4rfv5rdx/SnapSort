package xx.snapsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SortFoldersTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val best = SortFolder("Best", SortIcon.THUMB_UP)

    private fun photo(dir: File, name: String, text: String = name) = File(dir, name).apply { writeText(text) }

    @Test
    fun theFolderOnDiskCarriesTheDash() {
        assertEquals("-Best", best.dirName)
    }

    @Test
    fun aTypedNameIsMadeFitForAFolder() {
        assertEquals("Best", cleanSortName("  Best  "))
        assertEquals("Best", cleanSortName("-Best"))
        assertEquals("Best of 2024", cleanSortName("Best/of/2024".replace('/', ' ')))
        assertEquals("a b", cleanSortName("a/b"))
        assertEquals("", cleanSortName("   "))
        assertEquals("", cleanSortName("-.-"))
    }

    @Test
    fun foldersSurviveTheTripThroughPreferences() {
        val folders = listOf(best, SortFolder("Docs", SortIcon.DOCUMENT))
        assertEquals(folders, decodeSortFolders(encodeSortFolders(folders)))
    }

    @Test
    fun aLineThatCannotBeReadIsLeftOutAndNamesAreNotRepeated() {
        assertEquals(listOf(best), decodeSortFolders("NO_SUCH_ICON\tBest\nTHUMB_UP\tBest\nDOCUMENT\t  "))
        assertEquals(emptyList<SortFolder>(), decodeSortFolders(""))
    }

    @Test
    fun movingPutsThePhotoInTheFolderBesideIt() {
        val camera = tmp.newFolder("Camera")
        val file = photo(camera, "a.jpg", "pixels")

        val moved = SortMove.moveInto(listOf(file), best)?.single()

        assertEquals(File(camera, "-Best/a.jpg"), moved)
        assertEquals("pixels", moved!!.readText())
        assertFalse(file.exists())
    }

    @Test
    fun aTakenNameGetsANumberRatherThanOverwriting() {
        val camera = tmp.newFolder("Camera")
        photo(File(camera, "-Best").apply { mkdirs() }, "a.jpg", "first")
        val second = photo(camera, "a.jpg", "second")

        val moved = SortMove.moveInto(listOf(second), best)?.single()

        assertEquals(File(camera, "-Best/a (1).jpg"), moved)
        assertEquals("first", File(camera, "-Best/a.jpg").readText())
        assertEquals("second", moved!!.readText())
    }

    @Test
    fun movingBackReturnsThePhotoAndRefusesATakenPlace() {
        val camera = tmp.newFolder("Camera")
        val file = photo(camera, "a.jpg", "pixels")
        val moved = SortMove.moveInto(listOf(file), best)!!

        photo(camera, "a.jpg", "new")
        assertEquals(SortMove.BackResult.TARGET_EXISTS, SortMove.moveBack(moved, listOf(file)))
        assertEquals("pixels", moved.single().readText())

        file.delete()
        assertEquals(SortMove.BackResult.OK, SortMove.moveBack(moved, listOf(file)))
        assertEquals("pixels", file.readText())
        assertFalse(moved.single().exists())
    }

    @Test
    fun aFileWithNoFolderCannotBeMoved() {
        assertNull(SortMove.moveInto(listOf(File("a.jpg")), best))
    }

    @Test
    fun aRawGoesWithItsPhotoAndComesBackWithIt() {
        val camera = tmp.newFolder("Camera")
        val file = photo(camera, "a.jpg", "pixels")
        val raw = photo(camera, "a.dng", "raw")
        photo(camera, "b.dng", "other raw")
        photo(File(camera, "-Best").apply { mkdirs() }, "a.jpg", "first")
        val files = listOf(file) + companionsOf(file)
        assertEquals(listOf(file, raw), files)

        val moved = SortMove.moveInto(files, best)!!

        assertEquals(listOf("a (1).jpg", "a (1).dng"), moved.map { it.name })
        assertEquals("raw", moved[1].readText())
        assertFalse(raw.exists())

        assertEquals(SortMove.BackResult.OK, SortMove.moveBack(moved, files))
        assertEquals("raw", raw.readText())
        assertTrue(File(camera, "b.dng").exists())
    }

    @Test
    fun aRawHasNoCompanionsOfItsOwn() {
        val camera = tmp.newFolder("Camera")
        photo(camera, "a.jpg")
        assertEquals(emptyList<File>(), companionsOf(photo(camera, "a.dng")))
    }

    @Test
    fun sortedPhotosAreNotScannedBackIntoTheQueue() {
        val root = tmp.newFolder("pics")
        photo(root, "loose.jpg")
        photo(File(root, "-Best").apply { mkdirs() }, "kept.jpg")

        val found = ImageScanner.scan(root, ImageScanner.Progress()) {}

        assertEquals(listOf("loose.jpg"), found.map { it.file.name })
        // Picked as a root of its own, it is an ordinary folder again.
        assertTrue(ImageScanner.scan(File(root, "-Best"), ImageScanner.Progress()) {}.isNotEmpty())
    }
}
