package xx.snapsort

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's own trash: Android has no system trash for arbitrary files.
 * Every `root` here is the root of a storage volume, not the picked folder:
 * one trash per volume, whichever folder on it is being sorted.
 *
 *   <volume>/Documents/SnapSort/.Trash/<id>/<original name>   the item itself
 *   <volume>/Documents/SnapSort/.Trash/<id>.path               the record
 *
 * The record holds the original parent folder's path (to restore to) and a
 * human-readable relative path (to show). It lives beside the slot rather
 * than inside it, so no name the item could have collides with it.
 */
object Trash {
    /** Where the trash lives, relative to the volume root. */
    const val DIR_PATH = "Documents/SnapSort/.Trash"

    /** How long the trash keeps a photo before deleting it for good. */
    const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

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

    /** How far [empty] has got, read from another thread while it runs. */
    class EmptyProgress {
        @Volatile var total = 0
        val done = AtomicInteger(0)
    }

    /** Documents/SnapSort/.Trash under [root], created if it is not there yet.
     * Only for the one caller that needs somewhere to put a photo — reading
     * goes through [existingDir], so looking into a trash cannot conjure one. */
    fun dirFor(root: File): File {
        val dir = File(root, DIR_PATH)
        if (!dir.isDirectory) dir.mkdirs()
        return dir
    }

    /** The trash as it stands, or null when this root has never had one. */
    private fun existingDir(root: File): File? = File(root, DIR_PATH).takeIf { it.isDirectory }

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
        val trash = existingDir(root) ?: return emptyList()
        return trash.listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { slot -> readEntry(trash, slot) }
            .sortedByDescending { it.deletedAt }
    }

    /** One trash entry by id, or null if its slot or record is gone. */
    fun get(root: File, id: String): Entry? {
        val trash = existingDir(root) ?: return null
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

    /**
     * Deletes the whole trash folder, stray files included. Item by item rather
     * than one recursive delete, so [progress] can count them: a few hundred
     * photos take seconds. Nothing to empty counts as done.
     */
    fun empty(root: File, progress: EmptyProgress): Boolean {
        val trash = existingDir(root) ?: return true
        val slots = trash.listFiles { f -> f.isDirectory }.orEmpty()
        progress.total = slots.size
        for (slot in slots) {
            forget(trash, slot.name)
            progress.done.incrementAndGet()
        }
        return trash.deleteRecursively()
    }

    /**
     * Deletes everything trashed more than [MAX_AGE_MS] ago. A root that has
     * never had a trash lists as empty, so housekeeping never creates one.
     */
    fun purgeExpired(root: File, now: Long = System.currentTimeMillis()) {
        for (entry in list(root)) {
            if (now - entry.deletedAt > MAX_AGE_MS) purge(root, entry)
        }
    }

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
