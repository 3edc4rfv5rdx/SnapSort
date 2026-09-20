package xx.snapsort

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A line for the snackbar: a string resource, and an optional raw detail such as an exception message. */
class Notice(@param:StringRes val text: Int, val detail: String? = null)

/**
 * One photo sent to the trash this session, and where it stood in [SnapSortViewModel.images]
 * before it was spliced out — [SnapSortViewModel.undo] needs both to put it back in the same spot.
 */
private data class TrashedSlot(val index: Int, val entry: ImageEntry, val trashId: String)

private const val PROGRESS_POLL_MS = 200L

class SnapSortViewModel(app: Application) : AndroidViewModel(app) {

    var hasStorageAccess by mutableStateOf(false)
        private set

    var root by mutableStateOf<File?>(null)
        private set
    var images by mutableStateOf<List<ImageEntry>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set

    var scanning by mutableStateOf(false)
        private set
    var scannedCount by mutableIntStateOf(0)
        private set

    /** A trash, restore or purge is in flight; the queue must not move under it. */
    var busy by mutableStateOf(false)
        private set

    var notice by mutableStateOf<Notice?>(null)
        private set

    var trashOpen by mutableStateOf(false)
        private set
    var trashEntries by mutableStateOf<List<Trash.Entry>?>(null)
        private set

    var diskSpaceOpen by mutableStateOf(false)
        private set
    var diskFreeBytes by mutableLongStateOf(0L)
        private set
    var diskTotalBytes by mutableLongStateOf(0L)
        private set

    /** Every trash this session, most recent last; only [undo] pops it. */
    private val trashedStack = ArrayDeque<TrashedSlot>()
    val canUndo: Boolean get() = trashedStack.isNotEmpty()

    private var scanJob: Job? = null

    val current: ImageEntry? get() = images.getOrNull(index)
    val hasFolder: Boolean get() = root != null

    /**
     * Runs one file operation at a time, never letting it crash the app: a SAF
     * provider that rejects a call throws rather than returning a plain
     * failure, and every trash/restore/purge call goes through here so that
     * lands as [R.string.delete_failed] instead of a stack trace.
     */
    private fun runBusy(onFailure: Notice, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = onFailure
            } finally {
                busy = false
            }
        }
    }

    /**
     * Re-checked on every resume, since granting it happens in the system
     * Settings screen, not a dialog this activity gets a callback from.
     * Reopens the last folder the moment access turns from missing to
     * granted, so coming back from Settings does not need a second tap.
     */
    fun refreshStorageAccess() {
        val had = hasStorageAccess
        hasStorageAccess = hasAllFilesAccess()
        if (hasStorageAccess && !had) start()
    }

    /** Reopens the folder picked on a previous launch, if it is still there. */
    fun start() {
        val context = getApplication<Application>()
        val path = AppSettings.folderPath(context) ?: return
        loadFolder(File(path))
    }

    /** Called with the tree the user just picked — either the first pick, or a
     * later "change folder" from the menu. */
    fun openFolder(uri: Uri) {
        val context = getApplication<Application>()
        val dir = uri.treeToFile()
        if (dir == null) {
            notice = Notice(R.string.folder_unavailable)
            return
        }
        AppSettings.setFolderPath(context, dir.path)
        loadFolder(dir)
    }

    private fun loadFolder(dir: File) {
        if (!dir.isDirectory) {
            notice = Notice(R.string.folder_unavailable)
            return
        }
        root = dir
        rescan()
    }

    fun rescan() {
        val r = root ?: return
        scanJob?.cancel()
        scanning = true
        scannedCount = 0
        images = emptyList()
        index = 0
        trashedStack.clear()
        scanJob = viewModelScope.launch {
            val progress = ImageScanner.Progress()
            val ticker = launch {
                while (isActive) {
                    scannedCount = progress.files.get()
                    delay(PROGRESS_POLL_MS)
                }
            }
            try {
                val found = withContext(Dispatchers.IO) {
                    ImageScanner.scan(r, progress) { ensureActive() }
                }
                images = found.sortedWith(compareBy({ it.relativePath }, { it.file.name }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = Notice(R.string.scan_failed, detail = e.message)
            } finally {
                ticker.cancel()
                scanning = false
            }
        }
    }

    /** One photo forward — pure navigation, nothing on disk changes. */
    fun next() {
        if (images.isEmpty()) return
        index = (index + 1).coerceAtMost(images.lastIndex)
    }

    /** One photo back — pure navigation, nothing on disk changes. */
    fun previous() {
        if (images.isEmpty()) return
        index = (index - 1).coerceAtLeast(0)
    }

    /** Moves the current photo to the trash and drops it out of the queue. */
    fun trash() {
        val entry = current ?: return
        val r = root ?: return
        val at = index
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            val trashId = withContext(Dispatchers.IO) { Trash.moveToTrash(entry, r) }
            if (trashId != null) {
                images = images.toMutableList().also { it.removeAt(at) }
                trashedStack.addLast(TrashedSlot(at, entry, trashId))
                index = at.coerceAtMost((images.size - 1).coerceAtLeast(0))
            } else {
                notice = Notice(R.string.delete_failed)
            }
        }
    }

    /** Restores the most recently trashed photo and puts it back in the queue at the spot it left. */
    fun undo() {
        if (busy) return
        val slot = trashedStack.removeLastOrNull() ?: return
        val r = root ?: return
        runBusy(onFailure = Notice(R.string.restore_failed)) {
            val ok = withContext(Dispatchers.IO) {
                val entry = Trash.get(r, slot.trashId) ?: return@withContext false
                Trash.restore(r, entry) == Trash.RestoreResult.OK
            }
            if (ok) {
                images = images.toMutableList().also { it.add(slot.index.coerceIn(0, it.size), slot.entry) }
                index = slot.index.coerceIn(0, images.lastIndex)
            } else {
                trashedStack.addLast(slot)
                notice = Notice(R.string.restore_failed)
            }
        }
    }

    fun noticeShown() {
        notice = null
    }

    /** Space on the volume the current folder lives on, or the main storage volume before one is picked. */
    fun openDiskSpace() {
        val path = root?.path ?: Environment.getExternalStorageDirectory().path
        val stat = StatFs(path)
        diskTotalBytes = stat.totalBytes
        diskFreeBytes = stat.availableBytes
        diskSpaceOpen = true
    }

    fun closeDiskSpace() {
        diskSpaceOpen = false
    }

    // ---------- Trash screen ----------

    fun openTrash() {
        trashOpen = true
        reloadTrash()
    }

    fun closeTrash() {
        trashOpen = false
        trashEntries = null
    }

    private fun reloadTrash() {
        val r = root ?: return
        viewModelScope.launch {
            trashEntries = withContext(Dispatchers.IO) { Trash.list(r) }
        }
    }

    /**
     * The trash-screen entry may be the very photo [undo] on the swipe screen
     * would otherwise restore; dropping its slot here keeps that undo from
     * acting on a photo that already moved or is gone.
     */
    private fun forgetTrashedSlotOf(id: String) {
        trashedStack.removeAll { it.trashId == id }
    }

    fun restoreFromTrash(entry: Trash.Entry) {
        val r = root ?: return
        runBusy(onFailure = Notice(R.string.restore_failed)) {
            val result = withContext(Dispatchers.IO) { Trash.restore(r, entry) }
            notice = Notice(
                when (result) {
                    Trash.RestoreResult.OK -> R.string.restored
                    Trash.RestoreResult.TARGET_EXISTS -> R.string.restore_exists
                    Trash.RestoreResult.FAILED -> R.string.restore_failed
                },
            )
            if (result == Trash.RestoreResult.OK) {
                val slot = trashedStack.firstOrNull { it.trashId == entry.id }
                forgetTrashedSlotOf(entry.id)
                if (slot != null) {
                    images = images.toMutableList().also { it.add(slot.index.coerceIn(0, it.size), slot.entry) }
                }
                reloadTrash()
            }
        }
    }

    fun purgeFromTrash(entry: Trash.Entry) {
        val r = root ?: return
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            val ok = withContext(Dispatchers.IO) { Trash.purge(r, entry) }
            if (ok) {
                forgetTrashedSlotOf(entry.id)
            } else {
                notice = Notice(R.string.delete_failed)
            }
            reloadTrash()
        }
    }

    fun emptyTrash() {
        val r = root ?: return
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            val ok = withContext(Dispatchers.IO) { Trash.empty(r) }
            if (ok) {
                trashedStack.clear()
            } else {
                notice = Notice(R.string.delete_failed)
            }
            reloadTrash()
        }
    }
}
