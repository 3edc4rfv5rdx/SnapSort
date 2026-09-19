package xx.snapsort

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
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

/** A line for the snackbar: a string resource, and an optional raw detail such as an exception message. */
class Notice(@param:StringRes val text: Int, val detail: String? = null)

/** One step back for [SnapSortViewModel.undo]. */
private sealed interface HistoryStep {
    /** Keeping just moves on; undoing it only steps the index back. */
    data object Kept : HistoryStep
    /** Trashing must also be undone on disk, by the id [Trash.moveToTrash] returned. */
    data class Trashed(val trashId: String) : HistoryStep
}

private const val PROGRESS_POLL_MS = 200L

class SnapSortViewModel(app: Application) : AndroidViewModel(app) {

    var hasStorageAccess by mutableStateOf(false)
        private set

    var root by mutableStateOf<DocumentFile?>(null)
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

    private val history = ArrayDeque<HistoryStep>()
    var canUndo by mutableStateOf(false)
        private set

    private var scanJob: Job? = null

    val current: ImageEntry? get() = images.getOrNull(index)
    val hasFolder: Boolean get() = root != null
    val finished: Boolean get() = root != null && !scanning && images.isNotEmpty() && index >= images.size

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

    /** Reopens the folder picked on a previous launch, if the grant is still good. */
    fun start() {
        val context = getApplication<Application>()
        val uri = AppSettings.folderUri(context) ?: return
        val granted = context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }
        if (!granted) {
            AppSettings.setFolderUri(context, null)
            return
        }
        loadFolder(uri)
    }

    /** Called with the tree the user just picked. */
    fun openFolder(uri: Uri) {
        val context = getApplication<Application>()
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        AppSettings.setFolderUri(context, uri)
        loadFolder(uri)
    }

    private fun loadFolder(uri: Uri) {
        val context = getApplication<Application>()
        val doc = DocumentFile.fromTreeUri(context, uri)
        if (doc == null || !doc.isDirectory) {
            notice = Notice(R.string.folder_unavailable)
            return
        }
        root = doc
        rescan()
    }

    fun rescan() {
        val r = root ?: return
        scanJob?.cancel()
        scanning = true
        scannedCount = 0
        images = emptyList()
        index = 0
        history.clear()
        canUndo = false
        scanJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val progress = ImageScanner.Progress()
            val ticker = launch {
                while (isActive) {
                    scannedCount = progress.files.get()
                    delay(PROGRESS_POLL_MS)
                }
            }
            try {
                val found = withContext(Dispatchers.IO) {
                    ImageScanner.scan(context, r, progress) { ensureActive() }
                }
                images = found.sortedWith(compareBy({ it.relativePath }, { it.name }))
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

    /** Leaves the current photo where it is and moves on. */
    fun keep() {
        if (current == null || busy) return
        history.addLast(HistoryStep.Kept)
        canUndo = true
        index++
    }

    /** Moves the current photo to the trash and moves on. */
    fun trash() {
        val entry = current ?: return
        val r = root ?: return
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            val trashId = withContext(Dispatchers.IO) {
                Trash.moveToTrash(getApplication<Application>().contentResolver, entry, r)
            }
            if (trashId != null) {
                history.addLast(HistoryStep.Trashed(trashId))
                canUndo = true
                index++
            } else {
                notice = Notice(R.string.delete_failed)
            }
        }
    }

    /** Steps back one photo, restoring it out of the trash first if that is what sent it there. */
    fun undo() {
        if (busy) return
        val step = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        when (step) {
            HistoryStep.Kept -> index--
            is HistoryStep.Trashed -> {
                val r = root
                if (r == null) {
                    index--
                    return
                }
                runBusy(onFailure = Notice(R.string.restore_failed)) {
                    val context = getApplication<Application>()
                    val ok = withContext(Dispatchers.IO) {
                        val entry = Trash.get(context, r, step.trashId) ?: return@withContext false
                        Trash.restore(context, entry, r) == Trash.RestoreResult.OK
                    }
                    if (ok) {
                        index--
                    } else {
                        notice = Notice(R.string.restore_failed)
                    }
                }
            }
        }
    }

    fun noticeShown() {
        notice = null
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
            trashEntries = withContext(Dispatchers.IO) { Trash.list(getApplication(), r) }
        }
    }

    /**
     * The trash-screen entry may be the very photo an undo on the swipe
     * screen would otherwise restore; dropping its history step here keeps
     * that undo from acting on a slot that already moved or is gone.
     */
    private fun forgetHistoryOf(id: String) {
        if (history.remove(HistoryStep.Trashed(id))) canUndo = history.isNotEmpty()
    }

    fun restoreFromTrash(entry: Trash.Entry) {
        val r = root ?: return
        runBusy(onFailure = Notice(R.string.restore_failed)) {
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) { Trash.restore(context, entry, r) }
            notice = Notice(
                when (result) {
                    Trash.RestoreResult.OK -> R.string.restored
                    Trash.RestoreResult.TARGET_EXISTS -> R.string.restore_exists
                    Trash.RestoreResult.FAILED -> R.string.restore_failed
                },
            )
            if (result == Trash.RestoreResult.OK) {
                forgetHistoryOf(entry.id)
                reloadTrash()
            }
        }
    }

    fun purgeFromTrash(entry: Trash.Entry) {
        val r = root ?: return
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            val context = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) { Trash.purge(context, entry, r) }
            if (ok) {
                forgetHistoryOf(entry.id)
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
                if (history.removeAll { it is HistoryStep.Trashed }) canUndo = history.isNotEmpty()
            } else {
                notice = Notice(R.string.delete_failed)
            }
            reloadTrash()
        }
    }
}
