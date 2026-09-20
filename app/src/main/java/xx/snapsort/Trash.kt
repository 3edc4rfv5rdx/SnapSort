package xx.snapsort

import java.io.File
import java.io.IOException

/**
 * The app's own trash: Android has no system trash for arbitrary files.
 *
 *   <root>/Documents/SnapSort/.Trash/<id>/<original name>   the item itself
 *   <root>/Documents/SnapSort/.Trash/<id>.path               the record
 *
 * The record holds the original parent folder's path (to restore to) and a
 * human-readable relative path (to show). It lives beside the slot rather
 * than inside it, so no name the item could have collides with it.
 */
object Trash {
    /** Where the trash lives, relative to the granted root. */
    const val DIR_PATH = "Documents/SnapSort/.Trash"

    private const val RECORD_SUFFIX = ".path"

    class Entry(
        val id: String,
        val item: File,
        val originalParent: File,
        val originalPath: String,
        val deletedAt: Long,
        val size: Long,
    )

    enum class RestoreResult { OK, TARGET_EXISTS, FAILED }

    /** Documents/SnapSort/.Trash under [root], created if it is not there yet. */
    fun dirFor(root: File): File {
        val dir = File(root, DIR_PATH)
        if (!dir.isDirectory) dir.mkdirs()
        return dir
    }

    /** Moves [entry] into the trash of [root]. Returns the new entry's id, or null on failure. */
    fun moveToTrash(entry: ImageEntry, root: File): String? {
        val trash = dirFor(root)
        val parent = entry.file.parentFile ?: return null
        val id = freeId(trash)
        val slot = File(trash, id)
        if (!slot.mkdirs()) return null
        val record = File(trash, id + RECORD_SUFFIX)
        try {
            record.writeText("${parent.absolutePath}\n${entry.relativePath}")
        } catch (e: IOException) {
            record.delete()
            slot.delete()
            return null
        }
        val moved = entry.file.renameTo(File(slot, entry.file.name))
        if (!moved) {
            record.delete()
            slot.delete()
            return null
        }
        return id
    }

    /** What the trash holds, newest first. A slot with no readable record or item is left out. */
    fun list(root: File): List<Entry> {
        val trash = dirFor(root)
        return trash.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { slot -> readEntry(trash, slot) }
            .sortedByDescending { it.deletedAt }
    }

    /** One trash entry by id, or null if its slot or record is gone. */
    fun get(root: File, id: String): Entry? {
        val trash = dirFor(root)
        val slot = File(trash, id).takeIf { it.isDirectory } ?: return null
        return readEntry(trash, slot)
    }

    private fun readEntry(trash: File, slot: File): Entry? {
        val item = slot.listFiles()?.firstOrNull() ?: return null
        val record = File(trash, slot.name + RECORD_SUFFIX)
        if (!record.isFile) return null
        val text = try {
            record.readText()
        } catch (e: IOException) {
            return null
        }
        val lines = text.split("\n", limit = 2)
        val parent = lines.getOrNull(0)?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        val originalPath = lines.getOrNull(1).orEmpty()
        return Entry(slot.name, item, parent, originalPath, record.lastModified(), item.length())
    }

    /** Puts [entry] back where it came from, never over something that is there now. */
    fun restore(root: File, entry: Entry): RestoreResult {
        if (!entry.originalParent.isDirectory && !entry.originalParent.mkdirs()) return RestoreResult.FAILED
        val target = File(entry.originalParent, entry.item.name)
        if (target.exists()) return RestoreResult.TARGET_EXISTS
        if (!entry.item.renameTo(target)) return RestoreResult.FAILED
        forget(dirFor(root), entry.id)
        return RestoreResult.OK
    }

    /** Deletes one item for good. False when it could not be deleted. */
    fun purge(root: File, entry: Entry): Boolean {
        val ok = entry.item.delete()
        if (ok) forget(dirFor(root), entry.id)
        return ok
    }

    /** Deletes the whole trash folder, stray files included. */
    fun empty(root: File): Boolean = dirFor(root).deleteRecursively()

    private fun forget(trash: File, id: String) {
        File(trash, id).deleteRecursively()
        File(trash, id + RECORD_SUFFIX).delete()
    }

    private fun freeId(trash: File, now: Long = System.currentTimeMillis()): String {
        var id = now.toString()
        var n = 1
        while (File(trash, id).exists() || File(trash, id + RECORD_SUFFIX).exists()) {
            id = "$now-${n++}"
        }
        return id
    }
}
