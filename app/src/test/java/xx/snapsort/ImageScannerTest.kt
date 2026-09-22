package xx.snapsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImageScannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(root: File, path: String) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText("x")
    }

    private fun scan(root: File): List<ImageEntry> = ImageScanner.scan(root, ImageScanner.Progress()) {}

    @Test
    fun findsPhotosAndVideosWithTheirFolderAndIgnoresTheRest() {
        val root = tmp.newFolder("pics")
        file(root, "a.JPG")
        file(root, "trip/b.heic")
        file(root, "trip/day2/c.mp4")
        file(root, "trip/notes.txt")
        file(root, "trip/noextension")

        val found = scan(root).associate { it.file.name to it.relativePath }

        assertEquals(mapOf("a.JPG" to "", "b.heic" to "trip", "c.mp4" to "trip/day2"), found)
    }

    @Test
    fun skipsDotFoldersWhereverTheyAre() {
        val root = tmp.newFolder("pics")
        file(root, "keep.jpg")
        file(root, ".thumbnails/t.jpg")
        file(root, "trip/Documents/SnapSort/.Trash/123.jpg")

        assertEquals(listOf("keep.jpg"), scan(root).map { it.file.name })
    }

    @Test
    fun countsWhatItFinds() {
        val root = tmp.newFolder("pics")
        file(root, "a.jpg")
        file(root, "b.png")
        file(root, "c.txt")
        val progress = ImageScanner.Progress()

        ImageScanner.scan(root, progress) {}

        assertEquals(2, progress.files.get())
    }

    @Test
    fun videoIsToldByExtensionInAnyCase() {
        assertTrue(isVideo("/x/clip.MOV"))
        assertTrue(isVideo("/x/.Trash/123.mp4"))
        assertFalse(isVideo("/x/photo.jpg"))
    }

    @Test
    fun pathOnVolumeDropsTheVolumePrefixOnly() {
        val volume = File("/storage/emulated/0")
        assertEquals("DCIM/Camera", pathOnVolume(File("/storage/emulated/0/DCIM/Camera"), volume))
        assertEquals("/storage/emulated/0", pathOnVolume(volume, volume))
        assertEquals("/storage/1234-5678/DCIM", pathOnVolume(File("/storage/1234-5678/DCIM"), volume))
        // A sibling whose name only starts the same is not inside the volume.
        assertEquals("/storage/emulated/01/DCIM", pathOnVolume(File("/storage/emulated/01/DCIM"), volume))
        assertEquals("/a/b", pathOnVolume(File("/a/b"), null))
    }
}
