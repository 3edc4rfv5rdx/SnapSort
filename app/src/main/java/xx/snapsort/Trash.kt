package xx.snapsort

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/**
 * The app's own trash: Android has no system trash for arbitrary files, and
 * this app holds only a SAF grant, not raw filesystem access.
 *
 *   <root>/Documents/SnapSort/.Trash/<id>/<original name>   the item itself
 *   <root>/Documents/SnapSort/.Trash/<id>.path              the record
 *
 * The record holds the original parent folder's document Uri (to restore to)
 * and a human-readable path (to show). It lives beside the slot rather than
 * inside it, so no name the item could have collides with it.
 */
object Trash {
    /** Where the trash lives, relative to the granted root. */
    const val DIR_PATH = "Documents/SnapSort/.Trash"

    private const val RECORD_SUFFIX = ".path"
    // Not text/plain: some providers append an extension to a name they do not
    // already recognise as matching the mime type, which would rename the record.
    private const val RECORD_MIME = "application/octet-stream"

    class Entry(
        val id: String,
        val item: DocumentFile,
        val originalParent: Uri,
        val originalPath: String,
        val deletedAt: Long,
        val size: Long,
    )

    enum class RestoreResult { OK, TARGET_EXISTS, FAILED }

    // The trash dir is looked up on every operation in the same user action
    // (trash one photo, list, restore); cached by root so that costs one walk
    // of Documents/SnapSort/.Trash instead of three SAF round trips each time.
    private var cachedRootUri: Uri? = null
    private var cachedTrash: DocumentFile? = null

    /** Documents/SnapSort/.Trash under [root], created if it is not there yet. */
    fun dirFor(root: DocumentFile): DocumentFile? {
        cachedTrash?.takeIf { cachedRootUri == root.uri && it.isDirectory }?.let { return it }
        var dir = root
        for (segment in DIR_PATH.split('/')) {
            dir = dir.findFile(segment) ?: dir.createDirectory(segment) ?: return null
        }
        cachedRootUri = root.uri
        cachedTrash = dir
        return dir
    }

    /**
     * Moves [source] to [target] under [newParent], never throwing: some SAF
     * providers reject an unsupported move with an exception rather than a
     * plain null return.
     */
    private fun safeMove(resolver: ContentResolver, source: Uri, from: Uri, to: Uri): Uri? = try {
        DocumentsContract.moveDocument(resolver, source, from, to)
    } catch (e: Exception) {
        null
    }

    /** Moves [entry] into the trash of [root]. Returns the new entry's id, or null on failure. */
    fun moveToTrash(resolver: ContentResolver, entry: ImageEntry, root: DocumentFile): String? {
        val trash = dirFor(root) ?: return null
        val parent = entry.file.parentFile ?: return null
        val id = freeId(trash)
        val slot = trash.createDirectory(id) ?: return null
        val record = trash.createFile(RECORD_MIME, id + RECORD_SUFFIX)
        if (record == null) {
            slot.delete()
            return null
        }
        try {
            resolver.openOutputStream(record.uri)?.use {
                it.write("${parent.uri}\n${entry.relativePath}".toByteArray(Charsets.UTF_8))
            } ?: throw IOException("no output stream")
        } catch (e: IOException) {
            record.delete()
            slot.delete()
            return null
        }
        val name = entry.file.name
        val moved = safeMove(resolver, entry.file.uri, parent.uri, slot.uri)
        // A provider can perform the move but still answer with a null result
        // Uri; only when the photo is neither still at the source nor landed
        // in the slot did the move actually fail.
        val landed = moved != null || (name != null && parent.findFile(name) == null && slot.listFiles().isNotEmpty())
        if (!landed) {
            record.delete()
            slot.delete()
            return null
        }
        return id
    }

    /** What the trash holds, newest first. A slot with no readable record or item is left out. */
    fun list(context: Context, root: DocumentFile): List<Entry> {
        val trash = dirFor(root) ?: return emptyList()
        return trash.listFiles()
            .filter { it.isDirectory }
            .mapNotNull { slot -> readEntry(context, trash, slot) }
            .sortedByDescending { it.deletedAt }
    }

    /** One trash entry by id, or null if its slot or record is gone. */
    fun get(context: Context, root: DocumentFile, id: String): Entry? {
        val trash = dirFor(root) ?: return null
        val slot = trash.findFile(id)?.takeIf { it.isDirectory } ?: return null
        return readEntry(context, trash, slot)
    }

    private fun readEntry(context: Context, trash: DocumentFile, slot: DocumentFile): Entry? {
        val item = slot.listFiles().firstOrNull() ?: return null
        val record = trash.findFile(slot.name + RECORD_SUFFIX) ?: return null
        val text = try {
            context.contentResolver.openInputStream(record.uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: IOException) {
            null
        } ?: return null
        val lines = text.split("\n", limit = 2)
        val parent = lines.getOrNull(0)?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return null
        val originalPath = lines.getOrNull(1).orEmpty()
        return Entry(slot.name.orEmpty(), item, parent, originalPath, record.lastModified(), item.length())
    }

    /** Puts [entry] back where it came from, never over something that is there now. */
    fun restore(context: Context, entry: Entry, root: DocumentFile): RestoreResult {
        val trash = dirFor(root) ?: return RestoreResult.FAILED
        val slot = trash.findFile(entry.id) ?: return RestoreResult.FAILED
        val targetParent = DocumentFile.fromSingleUri(context, entry.originalParent)
            ?.takeIf { it.isDirectory } ?: return RestoreResult.FAILED
        val name = entry.item.name ?: return RestoreResult.FAILED
        if (targetParent.findFile(name) != null) return RestoreResult.TARGET_EXISTS
        val moved = safeMove(context.contentResolver, entry.item.uri, slot.uri, targetParent.uri)
        // Same null-but-actually-moved quirk as moveToTrash: trust the
        // filesystem, not just the return value.
        val landed = moved != null || (slot.findFile(name) == null && targetParent.findFile(name) != null)
        if (!landed) return RestoreResult.FAILED
        forget(trash, entry.id)
        return RestoreResult.OK
    }

    /** Deletes one item for good. False when it could not be deleted. */
    fun purge(context: Context, entry: Entry, root: DocumentFile): Boolean {
        val trash = dirFor(root) ?: return false
        val ok = entry.item.delete()
        if (ok) forget(trash, entry.id)
        return ok
    }

    /** Deletes the whole trash folder, stray files included. */
    fun empty(root: DocumentFile): Boolean {
        val ok = dirFor(root)?.delete() ?: true
        if (ok) {
            cachedTrash = null
            cachedRootUri = null
        }
        return ok
    }

    private fun forget(trash: DocumentFile, id: String) {
        trash.findFile(id)?.delete()
        trash.findFile(id + RECORD_SUFFIX)?.delete()
    }

    private fun freeId(trash: DocumentFile, now: Long = System.currentTimeMillis()): String {
        var id = now.toString()
        var n = 1
        while (trash.findFile(id) != null || trash.findFile(id + RECORD_SUFFIX) != null) {
            id = "$now-${n++}"
        }
        return id
    }
}
