package xx.snapsort

import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.atomic.AtomicInteger

/** One photo found under the granted tree. [file]'s own parent chain is intact,
 * since it was built by walking down from [ImageScanner.scan]'s root. */
class ImageEntry(val file: DocumentFile, val relativePath: String)

/**
 * Walks a SAF tree looking for images. There is no size to aggregate here, so
 * unlike a disk scan this just lists what it finds; [Trash.DIR_PATH] is
 * skipped, so a photo already thrown out never comes back into the queue.
 */
object ImageScanner {
    class Progress { val files = AtomicInteger(0) }

    fun scan(root: DocumentFile, progress: Progress, checkCancel: () -> Unit): List<ImageEntry> {
        val found = mutableListOf<ImageEntry>()
        walk(root, "", progress, checkCancel, found)
        return found
    }

    private fun walk(
        dir: DocumentFile,
        path: String,
        progress: Progress,
        checkCancel: () -> Unit,
        found: MutableList<ImageEntry>,
    ) {
        checkCancel()
        for (child in dir.listFiles()) {
            checkCancel()
            val name = child.name ?: continue
            val childPath = if (path.isEmpty()) name else "$path/$name"
            if (childPath == Trash.DIR_PATH) continue
            if (child.isDirectory) {
                walk(child, childPath, progress, checkCancel, found)
            } else if (child.type?.startsWith("image/") == true) {
                found += ImageEntry(child, path)
                progress.files.incrementAndGet()
            }
        }
    }
}
