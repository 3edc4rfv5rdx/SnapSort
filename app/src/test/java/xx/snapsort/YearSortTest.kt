package xx.snapsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar

class YearSortTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, text: String = name) = File(dir, name).apply { writeText(text) }

    private fun midYear(year: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, 5, 15)
    }.timeInMillis

    @Test
    fun theYearIsReadFromTheNamesCamerasAndAppsGive() {
        assertEquals(2019, yearFromName("IMG_20190512_143012.jpg"))
        assertEquals(2019, yearFromName("20190512_143012.jpg"))
        assertEquals(2019, yearFromName("PXL_20190512_143012345.jpg"))
        assertEquals(2019, yearFromName("IMG-20190512-WA0001.jpg"))
        assertEquals(2019, yearFromName("Screenshot_2019-05-12-14-30-12.png"))
        assertEquals(2019, yearFromName("IMG20190512143012.jpg"))
    }

    @Test
    fun digitsThatAreNoDateGiveNoYear() {
        assertNull(yearFromName("IMG_1234.jpg"))
        assertNull(yearFromName("1557661012345.jpg"))
        assertNull(yearFromName("IMG_20191332_000000.jpg"))
        assertNull(yearFromName("DSC_2019.jpg"))
    }

    @Test
    fun theCamerasRecordComesBeforeTheName() {
        val root = tmp.root
        val photo = file(root, "IMG_20190512_143012.jpg")
        assertEquals(2017, YearSort.yearOf(listOf(photo), 2026) { midYear(2017) })
        assertEquals(2019, YearSort.yearOf(listOf(photo), 2026) { null })
    }

    @Test
    fun aClockThatWasWrongCountsAsNoDate() {
        val photo = file(tmp.root, "IMG_1234.jpg")
        assertNull(YearSort.yearOf(listOf(photo), 2026) { midYear(1980) })
        assertNull(YearSort.yearOf(listOf(photo), 2026) { midYear(2030) })
        val named = file(tmp.root, "IMG_20190512_143012.jpg")
        assertEquals(2019, YearSort.yearOf(listOf(named), 2026) { midYear(1970) })
    }

    @Test
    fun aRawTakesTheYearOfItsPhoto() {
        val photo = file(tmp.root, "IMG_1.jpg")
        val raw = file(tmp.root, "IMG_1.dng")
        val group = YearSort.groups(tmp.root).single()
        assertEquals(listOf(photo, raw), group)
        assertEquals(2018, YearSort.yearOf(group, 2026) { if (it == photo) midYear(2018) else midYear(2015) })
    }

    @Test
    fun onlyTheTopLevelsPhotosVideosAndRawsAreTaken() {
        val root = tmp.root
        file(root, "a.jpg")
        file(root, "b.mp4")
        file(root, "c.dng")
        file(root, "notes.txt")
        file(root, ".pending-1-d.jpg")
        file(File(root, "Trip").apply { mkdirs() }, "e.jpg")
        assertEquals(listOf("a.jpg", "b.mp4", "c.dng"), YearSort.groups(root).map { it.single().name })
    }

    @Test
    fun thisYearAndUndatedStay() {
        val a = listOf(File("a.jpg"))
        val b = listOf(File("b.jpg"), File("b.dng"))
        val c = listOf(File("c.jpg"))
        val d = listOf(File("d.jpg"))
        val plan = YearSort.plan(listOf(a, b, c, d), listOf(2019, 2018, 2026, null), 2026)
        assertEquals(listOf(2018, 2019), plan.moves.keys.toList())
        assertEquals(mapOf(2018 to 2, 2019 to 1), plan.filesByYear)
        assertEquals(1, plan.thisYearFiles)
        assertEquals(1, plan.undatedFiles)
    }

    @Test
    fun aGroupMovesUnderOneFreeName() {
        val root = tmp.root
        val photo = file(root, "IMG_1.jpg", "new photo")
        val raw = file(root, "IMG_1.dng", "new raw")
        val year = File(root, "2019").apply { mkdirs() }
        file(year, "IMG_1.jpg", "old photo")
        val moved = YearSort.moveGroup(listOf(photo, raw), root, 2019)!!
        assertEquals(listOf("IMG_1 (1).jpg", "IMG_1 (1).dng"), moved.map { it.name })
        assertEquals("new photo", moved[0].readText())
        assertEquals("new raw", moved[1].readText())
        assertEquals("old photo", File(year, "IMG_1.jpg").readText())
        assertFalse(photo.exists())
        assertFalse(raw.exists())
    }

    @Test
    fun theYearFolderIsMadeWhenMissing() {
        val photo = file(tmp.root, "a.jpg")
        val moved = YearSort.moveGroup(listOf(photo), tmp.root, 2018)!!
        assertEquals(File(tmp.root, "2018/a.jpg"), moved.single())
        assertTrue(moved.single().exists())
    }

    @Test
    fun aYearFolderThatCannotBeMadeMovesNothing() {
        val photo = file(tmp.root, "a.jpg")
        file(tmp.root, "2018")
        assertNull(YearSort.moveGroup(listOf(photo), tmp.root, 2018))
        assertTrue(photo.exists())
    }
}
