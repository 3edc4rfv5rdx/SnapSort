package xx.snapsort

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** One photo found under the granted folder. */
class ImageEntry(val file: File, val relativePath: String)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

/** Walks a plain folder tree looking for images. Dot-directories are skipped,
 * which covers the app's own trash wherever a past root left it, so a photo
 * already thrown out never comes back into the queue, and machinery such as
 * .thumbnails never reaches it either. */
object ImageScanner {
    class Progress { val files = AtomicInteger(0) }

    fun scan(root: File, progress: Progress, checkCancel: () -> Unit): List<ImageEntry> {
        val found = mutableListOf<ImageEntry>()
        walk(root, "", progress, checkCancel, found)
        return found
    }

    private fun walk(
        dir: File,
        path: String,
        progress: Progress,
        checkCancel: () -> Unit,
        found: MutableList<ImageEntry>,
    ) {
        checkCancel()
        val children = dir.listFiles() ?: return
        for (child in children) {
            checkCancel()
            if (child.isDirectory) {
                // Matching the trash by its exact path only worked while the
                // root stayed put: pick a root one level up and the old trash
                // became an ordinary folder, handing every discarded photo
                // back to the queue. Its name is what identifies it.
                if (child.name.startsWith(".")) continue
                val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
                walk(child, childPath, progress, checkCancel, found)
            } else if (child.extension.lowercase() in IMAGE_EXTENSIONS) {
                found += ImageEntry(child, path)
                progress.files.incrementAndGet()
            }
        }
    }
}
