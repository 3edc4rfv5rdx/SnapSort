package xx.snapsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Stands in for a storage volume's root. */
    private lateinit var root: File
    private lateinit var camera: File

    @Before
    fun setUp() {
        root = tmp.newFolder("volume")
        camera = File(root, "DCIM/Camera").apply { mkdirs() }
    }

    private fun photo(name: String, text: String = name): ImageEntry {
        val file = File(camera, name).apply { writeText(text) }
        return ImageEntry(file, "DCIM/Camera")
    }

    private fun trash(entry: ImageEntry): String =
        Trash.moveToTrash(entry, root) ?: throw AssertionError("could not trash ${entry.file}")

    @Test
    fun movedPhotoLeavesItsFolderAndIsListed() {
        val entry = photo("a.jpg", "pixels")
        val id = trash(entry)

        assertFalse(entry.file.exists())
        val listed = Trash.list(root).single()
        assertEquals(id, listed.id)
        assertEquals("a.jpg", listed.name)
        assertEquals(camera.absolutePath, listed.originalParent.path)
        assertEquals("pixels", listed.item.readText())
        // The item keeps its extension, so a file manager still sees a photo.
        assertEquals("jpg", listed.item.extension)
    }

    @Test
    fun twoPhotosOfOneNameGetTwoIds() {
        val first = trash(photo("a.jpg", "first"))
        val second = trash(photo("a.jpg", "second"))

        assertNotEquals(first, second)
        assertEquals(setOf("first", "second"), Trash.list(root).map { it.item.readText() }.toSet())
    }

    @Test
    fun restorePutsThePhotoBackAndForgetsIt() {
        val entry = photo("a.jpg", "pixels")
        val id = trash(entry)

        assertEquals(Trash.RestoreResult.OK, Trash.restore(Trash.get(root, id)!!))
        assertEquals("pixels", entry.file.readText())
        assertTrue(Trash.list(root).isEmpty())
    }

    @Test
    fun restoreNeverOverwritesWhatIsThereNow() {
        val entry = photo("a.jpg", "old")
        val id = trash(entry)
        entry.file.writeText("new")

        assertEquals(Trash.RestoreResult.TARGET_EXISTS, Trash.restore(Trash.get(root, id)!!))
        assertEquals("new", entry.file.readText())
        assertEquals(id, Trash.list(root).single().id)
    }

    @Test
    fun restoreRecreatesAFolderRemovedSince() {
        val entry = photo("a.jpg")
        val id = trash(entry)
        camera.deleteRecursively()

        assertEquals(Trash.RestoreResult.OK, Trash.restore(Trash.get(root, id)!!))
        assertTrue(entry.file.isFile)
    }

    @Test
    fun restoreAllLeavesATakenPlaceInTheTrashAndCountsIt() {
        val kept = photo("a.jpg")
        val blocked = photo("b.jpg", "old")
        val keptId = trash(kept)
        val blockedId = trash(blocked)
        blocked.file.writeText("new")
        val progress = Trash.Progress()

        val result = Trash.restoreAll(root, progress)

        assertEquals(listOf(keptId), result.restoredIds)
        assertEquals(1, result.notRestored)
        assertEquals(2, progress.total)
        assertEquals(2, progress.done.get())
        assertTrue(kept.file.isFile)
        assertEquals("new", blocked.file.readText())
        assertEquals(blockedId, Trash.list(root).single().id)
    }

    @Test
    fun emptyRemovesEverythingAndCountsThePhotosNotTheRecords() {
        trash(photo("a.jpg"))
        trash(photo("b.jpg"))
        val progress = Trash.Progress()

        assertTrue(Trash.empty(root, progress))
        assertEquals(2, progress.total)
        assertEquals(2, progress.done.get())
        assertFalse(File(root, Trash.DIR_PATH).exists())
    }

    @Test
    fun purgeExpiredDeletesOnlyWhatIsOlderThanTheLimit() {
        val old = trash(photo("old.jpg"))
        // The id is the deletion time in milliseconds: apart, so one moment splits them.
        Thread.sleep(5)
        val fresh = trash(photo("fresh.jpg"))
        val oldAt = Trash.get(root, old)!!.deletedAt

        Trash.purgeExpired(root, now = oldAt + Trash.MAX_AGE_MS + 1)

        assertEquals(listOf(fresh), Trash.list(root).map { it.id })
    }

    @Test
    fun lookingIntoAVolumeWithoutATrashDoesNotCreateOne() {
        assertTrue(Trash.list(root).isEmpty())
        assertNull(Trash.get(root, "123"))
        Trash.purgeExpired(root)
        assertFalse(File(root, Trash.DIR_PATH).exists())
    }

    @Test
    fun aRecordWhoseItemIsGoneIsLeftOut() {
        val id = trash(photo("a.jpg"))
        Trash.get(root, id)!!.item.delete()

        assertTrue(Trash.list(root).isEmpty())
        assertNull(Trash.get(root, id))
    }
}
