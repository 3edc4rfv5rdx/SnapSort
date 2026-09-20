package xx.snapsort

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** One photo found under the granted folder. */
class ImageEntry(val file: File, val relativePath: String)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

/** Walks a plain folder tree looking for images. [Trash.DIR_PATH] is skipped,
 * so a photo already thrown out never comes back into the queue. */
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
            val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
            if (childPath == Trash.DIR_PATH) continue
            if (child.isDirectory) {
                walk(child, childPath, progress, checkCancel, found)
            } else if (child.extension.lowercase() in IMAGE_EXTENSIONS) {
                found += ImageEntry(child, path)
                progress.files.incrementAndGet()
            }
        }
    }
}
