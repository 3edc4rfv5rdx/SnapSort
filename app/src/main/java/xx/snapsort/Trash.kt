package xx.snapsort

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's own trash: Android has no system trash for arbitrary files.
 * Every `root` here is the root of a storage volume, not the picked folder:
 * one trash per volume, whichever folder on it is being sorted.
 *
 *   <volume>/Documents/SnapSort/.Trash/<id>.<ext>   the item, renamed
 *   <volume>/Documents/SnapSort/.Trash/<id>.dng     its RAW, if it had one
 *   <volume>/Documents/SnapSort/.Trash/<id>.path    the record
 *
 * The id is the moment of deletion in epoch milliseconds, with a "-n" suffix
 * in the rare case two land on the same one, so no two items ever collide
 * whatever their names were. The item keeps its extension so a file manager
 * still sees a photo. The record holds the original parent folder's path and
 * the original name, to restore it and to show it, then the names of the
 * [companionsOf] that went with it, one per line — they share its id and come
 * back with it. A record written before that has no such lines.
 */
object Trash {
    /** Where the trash lives, relative to the volume root. */
    const val DIR_PATH = "Documents/SnapSort/.Trash"

    /** How long the trash keeps a photo before deleting it for good. */
    const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    private const val RECORD_SUFFIX = ".path"

    /** [companions] are the items that went in with [item], each with its original name; [size] counts them too. */
    class Entry(
        val id: String,
        val item: File,
        val record: File,
        val name: String,
        val originalParent: File,
        val deletedAt: Long,
        val size: Long,
        val companions: List<Pair<File, String>> = emptyList(),
    )

    enum class RestoreResult { OK, TARGET_EXISTS, FAILED }

    /** How far [empty] or [restoreAll] has got, read from another thread while it runs. */
    class Progress {
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

    /**
     * Moves [entry] into the trash of [root], and [companions] with it, all or
     * none. Returns the new entry's id, or null on failure.
     */
    fun moveToTrash(entry: ImageEntry, root: File, companions: List<File> = companionsOf(entry.file)): String? {
        val trash = dirFor(root)
        val parent = entry.file.parentFile ?: return null
        val files = listOf(entry.file) + companions
        val names = files.map { it.name }
        val id = freeId(trash, names)
        val record = File(trash, id + RECORD_SUFFIX)
        try {
            record.writeText((listOf(parent.absolutePath) + names).joinToString("\n"))
        } catch (e: IOException) {
            record.delete()
            return null
        }
        if (!SortMove.renameAll(files, names.map { File(trash, itemName(id, it)) })) {
            record.delete()
            return null
        }
        return id
    }

    /** What the trash holds, newest first. A record with no readable item is left out. */
    fun list(root: File): List<Entry> {
        val trash = existingDir(root) ?: return emptyList()
        return trash.listFiles { f -> f.name.endsWith(RECORD_SUFFIX) }.orEmpty()
            .mapNotNull { record -> readEntry(trash, record.name.removeSuffix(RECORD_SUFFIX)) }
            .sortedByDescending { it.deletedAt }
    }

    /** One trash entry by id, or null if its item or record is gone. */
    fun get(root: File, id: String): Entry? {
        val trash = existingDir(root) ?: return null
        return readEntry(trash, id)
    }

    private fun readEntry(trash: File, id: String): Entry? {
        val record = File(trash, id + RECORD_SUFFIX)
        val text = try {
            record.readText()
        } catch (e: IOException) {
            return null
        }
        val lines = text.split("\n")
        val parent = lines.getOrNull(0)?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        val name = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val item = File(trash, itemName(id, name)).takeIf { it.isFile } ?: return null
        val companions = lines.drop(2).filter { it.isNotBlank() }
            .mapNotNull { companion -> File(trash, itemName(id, companion)).takeIf { it.isFile }?.let { it to companion } }
        // The id is the deletion time; the record's mtime only for an id that
        // is somehow not a number, since copying the trash would reset it.
        val deletedAt = id.substringBefore('-').toLongOrNull() ?: record.lastModified()
        val size = item.length() + companions.sumOf { it.first.length() }
        return Entry(id, item, record, name, parent, deletedAt, size, companions)
    }

    /**
     * Puts [entry] back where it came from, its companions with it, each under
     * its own name, never over something that is there now.
     */
    fun restore(entry: Entry): RestoreResult {
        if (!entry.originalParent.isDirectory && !entry.originalParent.mkdirs()) return RestoreResult.FAILED
        val items = listOf(entry.item) + entry.companions.map { it.first }
        val targets = (listOf(entry.name) + entry.companions.map { it.second }).map { File(entry.originalParent, it) }
        if (targets.any { it.exists() }) return RestoreResult.TARGET_EXISTS
        if (!SortMove.renameAll(items, targets)) return RestoreResult.FAILED
        entry.record.delete()
        return RestoreResult.OK
    }

    /** The ids [restoreAll] put back, and how many it could not. */
    class RestoreAllResult(val restoredIds: List<String>, val notRestored: Int)

    /**
     * Puts everything in the trash of [root] back, one [restore] at a time: an
     * item whose place is taken, or that will not move, stays in the trash and
     * is counted instead of stopping the rest.
     */
    fun restoreAll(root: File, progress: Progress): RestoreAllResult {
        val entries = list(root)
        progress.total = entries.size
        val restored = mutableListOf<String>()
        for (entry in entries) {
            if (restore(entry) == RestoreResult.OK) restored += entry.id
            progress.done.incrementAndGet()
        }
        return RestoreAllResult(restored, entries.size - restored.size)
    }

    /** Deletes one item for good, with its companions. False when it could not be deleted. */
    fun purge(entry: Entry): Boolean {
        val ok = entry.item.delete()
        if (ok) {
            entry.companions.forEach { it.first.delete() }
            entry.record.delete()
        }
        return ok
    }

    /**
     * Deletes the whole trash folder, stray files included. File by file rather
     * than one recursive delete, so [progress] can count the photos: a few
     * hundred take seconds. Nothing to empty counts as done.
     */
    fun empty(root: File, progress: Progress): Boolean {
        val trash = existingDir(root) ?: return true
        val files = trash.listFiles().orEmpty()
        progress.total = files.count { !it.name.endsWith(RECORD_SUFFIX) }
        for (file in files) {
            file.deleteRecursively()
            if (!file.name.endsWith(RECORD_SUFFIX)) progress.done.incrementAndGet()
        }
        return trash.deleteRecursively()
    }

    /**
     * Deletes everything trashed more than [MAX_AGE_MS] ago. A root that has
     * never had a trash lists as empty, so housekeeping never creates one.
     */
    fun purgeExpired(root: File, now: Long = System.currentTimeMillis()) {
        for (entry in list(root)) {
            if (now - entry.deletedAt > MAX_AGE_MS) purge(entry)
        }
    }

    /** The item's file name: the id plus the original name's extension, if it has one. */
    private fun itemName(id: String, name: String): String {
        val ext = name.substringAfterLast('.', "")
        return if (ext.isEmpty()) id else "$id.$ext"
    }

    private fun freeId(trash: File, names: List<String>, now: Long = System.currentTimeMillis()): String {
        var id = now.toString()
        var n = 1
        // The items too, not just the record: a rename onto a stray file of
        // the same name would silently replace it.
        while (File(trash, id + RECORD_SUFFIX).exists() || names.any { File(trash, itemName(id, it)).exists() }) {
            id = "$now-${n++}"
        }
        return id
    }
}
