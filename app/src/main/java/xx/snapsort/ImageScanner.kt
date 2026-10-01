package xx.snapsort

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** One photo or video found under the granted folder. */
class ImageEntry(val file: File, val relativePath: String)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "3gp", "webm", "mkv", "mov")

/** Whether [path] names a video: sorted like a photo, shown as a still, played in another app.
 * By extension, which a trashed file keeps, so this holds inside the trash too. */
fun isVideo(path: String): Boolean = File(path).extension.lowercase() in VIDEO_EXTENSIONS

/** RAW files a camera writes beside its JPEG: not in the queue, but moved with their photo. */
private val RAW_EXTENSIONS = setOf("dng")

fun isRaw(file: File): Boolean = file.extension.lowercase() in RAW_EXTENSIONS

/**
 * The RAW files beside [file] under its name, which go wherever it goes —
 * into the trash, a sorting folder, and back — so a pair is never split. A
 * RAW has none of its own: it is the one that follows.
 */
fun companionsOf(file: File): List<File> {
    if (isRaw(file)) return emptyList()
    val dir = file.parentFile ?: return emptyList()
    val base = file.nameWithoutExtension
    // Listed, not guessed at as base.dng and base.DNG: shared storage ignores
    // case, so both guesses would find the one file.
    return dir.listFiles { f -> f.nameWithoutExtension == base && isRaw(f) && f.isFile }.orEmpty().sortedBy { it.name }
}

/** Whether [file] is a photo or video the queue takes, by extension. */
fun isMedia(file: File): Boolean = file.extension.lowercase().let { it in IMAGE_EXTENSIONS || it in VIDEO_EXTENSIONS }

/** Walks a plain folder tree looking for images and videos — or, without [scan]'s
 * subfolders, only the folder itself. Directories whose name
 * starts with a dot or a dash are skipped: the first covers the app's own trash
 * wherever a past root left it and machinery such as .thumbnails, the second the
 * folders photos are sorted into ([SortFolder]). So a photo already thrown out or
 * put away never comes back into the queue. Either one can still be picked as a
 * root of its own, to go through what is in it. */
object ImageScanner {
    class Progress { val files = AtomicInteger(0) }

    /** [sortFolders]: go into the folders photos are sorted into as well — a search for copies needs them. */
    fun scan(
        root: File,
        progress: Progress,
        subfolders: Boolean = true,
        sortFolders: Boolean = false,
        checkCancel: () -> Unit,
    ): List<ImageEntry> {
        val found = mutableListOf<ImageEntry>()
        walk(root, "", progress, subfolders, sortFolders, checkCancel, found)
        return found
    }

    private fun walk(
        dir: File,
        path: String,
        progress: Progress,
        subfolders: Boolean,
        sortFolders: Boolean,
        checkCancel: () -> Unit,
        found: MutableList<ImageEntry>,
    ) {
        checkCancel()
        val children = dir.listFiles() ?: return
        for (child in children) {
            checkCancel()
            if (child.isDirectory) {
                if (!subfolders) continue
                // Matching the trash by its exact path only worked while the
                // root stayed put: pick a root one level up and the old trash
                // became an ordinary folder, handing every discarded photo
                // back to the queue. The name is what identifies both kinds.
                if (child.name.startsWith(".") || (!sortFolders && child.name.startsWith(SORT_DIR_PREFIX))) continue
                val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
                walk(child, childPath, progress, subfolders = true, sortFolders, checkCancel, found)
            } else if (isMedia(child)) {
                found += ImageEntry(child, path)
                progress.files.incrementAndGet()
            }
        }
    }
}
