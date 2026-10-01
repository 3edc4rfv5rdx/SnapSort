package xx.snapsort

import android.app.Application
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xx.snapsort.ui.formatCount
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A line for the snackbar: a string resource with its format [args], if it
 * has any, and an optional raw detail such as an exception message.
 */
class Notice(@param:StringRes val text: Int, val detail: String? = null, val args: List<Any> = emptyList())

/** What a whole-trash job in flight is doing, for the progress it shows in place of the list. */
enum class TrashJob { EMPTY, RESTORE }

/** Where a sort into year folders is: reading the dates for its plan, or moving the files once it is agreed. */
enum class YearSortPhase { READING, MOVING }

/**
 * Where a search for groups is: looking for files ([GroupKind.DUPLICATES]),
 * reading when each was taken ([GroupKind.SIMILAR]), or comparing them.
 */
enum class SimilarPhase { SEARCHING, READING_DATES, COMPARING }

/** What the groups on the similar-shots screen are: shots that look alike, or byte-for-byte copies. */
enum class GroupKind { SIMILAR, DUPLICATES }

/**
 * One photo taken out of the queue this session — thrown away or sorted into a
 * folder — and where it stood in [SnapSortViewModel.images] before it was
 * spliced out, which [SnapSortViewModel.undo] needs to put it back in the same
 * spot. One history for both, so undo walks back through them in order.
 */
private sealed interface Step {
    val index: Int
    val entry: ImageEntry

    /**
     * [size] is what it counted for in the session's tally, to take back out
     * if it comes back. Steps sharing a [batch] other than 0 went in one tap —
     * the rest of a group of similar shots — and one undo takes them all back.
     * An [index] of -1: the file was never in the queue — a copy found
     * elsewhere on the volume — and comes back to its folder only.
     */
    data class Trashed(
        override val index: Int,
        override val entry: ImageEntry,
        val trashId: String,
        val size: Long,
        val batch: Long = 0L,
    ) : Step

    /** [movedFrom] — the photo and its RAW — are now at [movedTo], file for file; [entry] is the photo as it was. */
    data class Moved(
        override val index: Int,
        override val entry: ImageEntry,
        val movedFrom: List<File>,
        val movedTo: List<File>,
    ) : Step
}

private const val PROGRESS_POLL_MS = 200L

/** Dates are read a few files at a time: the reads are mostly waiting on storage. */
private val dateDispatcher = Dispatchers.IO.limitedParallelism(4)

class SnapSortViewModel(app: Application) : AndroidViewModel(app) {

    var hasStorageAccess by mutableStateOf(false)
        private set

    var root by mutableStateOf<File?>(null)
        private set

    /** The root of the volume [root] is on. The trash lives there, not under
     * [root], so every folder picked on one volume shares the one trash; and
     * paths are shown relative to it. */
    var volumeRoot by mutableStateOf<File?>(null)
        private set

    var images by mutableStateOf<List<ImageEntry>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set

    var scanning by mutableStateOf(false)
        private set
    var scannedCount by mutableIntStateOf(0)
        private set

    /** Part of [scanning] for a date order: [datesRead] of [datesTotal] files have had their date read so far. */
    var readingDates by mutableStateOf(false)
        private set
    var datesRead by mutableIntStateOf(0)
        private set
    var datesTotal by mutableIntStateOf(0)
        private set

    /** Each file's date taken by path, read once: a new scan or a change of order does not open the file again. */
    private val takenAtCache = ConcurrentHashMap<String, Long>()

    /** A trash, restore or purge is in flight; the queue must not move under it. */
    var busy by mutableStateOf(false)
        private set

    var notice by mutableStateOf<Notice?>(null)
        private set

    var trashOpen by mutableStateOf(false)
        private set
    var trashEntries by mutableStateOf<List<Trash.Entry>?>(null)
        private set

    /** [emptyTrash] or [restoreAllFromTrash] is running; [trashJobDone] of [trashJobTotal] items are done so far. */
    var trashJob by mutableStateOf<TrashJob?>(null)
        private set
    var trashJobDone by mutableIntStateOf(0)
        private set
    var trashJobTotal by mutableIntStateOf(0)
        private set

    var diskSpaceOpen by mutableStateOf(false)
        private set
    var diskFreeBytes by mutableLongStateOf(0L)
        private set
    var diskTotalBytes by mutableLongStateOf(0L)
        private set

    /** What the trash of the current volume holds, read when the disk-space dialog opens; null without a folder. */
    var trashCount by mutableStateOf<Int?>(null)
        private set
    var trashBytes by mutableLongStateOf(0L)
        private set

    /**
     * Photos sent to the trash since the app started, less those put back from
     * it — by undo or from the trash screen. Emptying the trash does not take
     * them off: they were still thrown out, the space is only now given back.
     */
    var sessionTrashedCount by mutableIntStateOf(0)
        private set
    var sessionTrashedBytes by mutableLongStateOf(0L)
        private set

    /** A sort into year folders is in flight; [yearSortDone] of [yearSortTotal] are done so far. */
    var yearSortPhase by mutableStateOf<YearSortPhase?>(null)
        private set
    var yearSortDone by mutableIntStateOf(0)
        private set
    var yearSortTotal by mutableIntStateOf(0)
        private set

    /** What a sort into year folders would do, waiting for OK; null when there is nothing to ask. */
    var yearPlan by mutableStateOf<YearPlan?>(null)
        private set

    private var yearSortJob: Job? = null

    /** The similar-shots screen is open: searching while [similarPhase] is set, then going through [similarGroups]. */
    var similarOpen by mutableStateOf(false)
        private set
    var similarPhase by mutableStateOf<SimilarPhase?>(null)
        private set
    var similarDone by mutableIntStateOf(0)
        private set
    var similarTotal by mutableIntStateOf(0)
        private set
    var similarGroups by mutableStateOf<List<List<ImageEntry>>>(emptyList())
        private set

    /** The group on screen, in [similarGroups]. */
    var similarIndex by mutableIntStateOf(0)
        private set

    var groupKind by mutableStateOf(GroupKind.SIMILAR)
        private set

    private var similarJob: Job? = null

    /** Photos turned this session: a change tells a photo on screen to decode again, as its file did not move. */
    var rotations by mutableIntStateOf(0)
        private set

    /** Every photo taken out of the queue this session, most recent last; only [undo] pops it. */
    private val history = ArrayDeque<Step>()
    val canUndo: Boolean get() = history.isNotEmpty()

    private val trashed: List<Step.Trashed> get() = history.filterIsInstance<Step.Trashed>()

    private var scanJob: Job? = null

    init {
        // Only a change: the order in force when the folder opens is applied by the scan itself.
        viewModelScope.launch { AppSettings.queueOrder.drop(1).collect { reorder() } }
        // A different set of files: scanned again, the photo on screen kept if it is still in.
        viewModelScope.launch {
            AppSettings.subfolders.drop(1).collect {
                val onScreen = current?.file
                rescan { queue -> queue.indexOfFirst { it.file == onScreen } }
            }
        }
    }

    val current: ImageEntry? get() = images.getOrNull(index)

    /** [files] moved, gone or changed: for the galleries, which go by the system's index of them. */
    private fun announce(files: List<File>) = announceChanged(getApplication(), files.map { it.path })

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
        loadFolder(File(path), restorePosition = true)
    }

    /** Called with the folder the user just chose — either the first pick, or a
     * later "change folder" from the menu. Always starts at the first photo:
     * a saved position only means something for the same folder reopened by
     * [start], not a fresh pick. */
    fun openFolder(dir: File) {
        val context = getApplication<Application>()
        if (!dir.isDirectory) {
            notice = Notice(R.string.folder_unavailable)
            return
        }
        AppSettings.setFolderPath(context, dir.path)
        // The saved position belongs to the folder it was saved for. Without
        // this, a pick that is never browsed leaves the old folder's index in
        // place, and the next launch restores it against the new folder —
        // landing on its last photo instead of its first.
        AppSettings.setLastPosition(context, 0, null)
        loadFolder(dir, restorePosition = false)
    }

    private fun loadFolder(dir: File, restorePosition: Boolean) {
        if (!dir.isDirectory) {
            notice = Notice(R.string.folder_unavailable)
            return
        }
        root = dir
        val trashDir = volumeRootOf(getApplication(), dir) ?: dir
        volumeRoot = trashDir
        // Housekeeping, not part of the scan: an expired photo goes whether or
        // not the trash screen is ever opened.
        viewModelScope.launch { withContext(Dispatchers.IO) { Trash.purgeExpired(trashDir) } }
        rescan(restorePosition)
    }

    /** Scans [root] again. [landOn] picks the photo to show after from the new queue; -1 leaves it to the rest. */
    fun rescan(restorePosition: Boolean = false, landOn: ((List<ImageEntry>) -> Int)? = null) {
        val r = root ?: return
        scanJob?.cancel()
        scanning = true
        scannedCount = 0
        images = emptyList()
        index = 0
        history.clear()
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
                    ImageScanner.scan(r, progress, AppSettings.subfolders.value) { ensureActive() }
                }
                images = ordered(found)
                val landed = landOn?.invoke(images) ?: -1
                if (landed in images.indices) {
                    moveTo(landed)
                } else if (restorePosition && images.isNotEmpty() && AppSettings.rememberPosition.value) {
                    val context = getApplication<Application>()
                    val saved = AppSettings.lastPath(context)
                    val byPath = if (saved == null) -1 else images.indexOfFirst { it.file.path == saved }
                    index = if (byPath >= 0) byPath else AppSettings.lastIndex(context).coerceIn(0, images.lastIndex)
                }
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

    /** The order changed in Settings: the same queue re-sorted, with the photo on screen kept on screen. */
    private fun reorder() {
        // A scan in flight sorts by the new order itself when it gets there.
        if (scanning || images.isEmpty()) return
        val onScreen = current
        val queue = images
        scanning = true
        scanJob = viewModelScope.launch {
            try {
                images = ordered(queue)
                moveTo(images.indexOf(onScreen).coerceAtLeast(0))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = Notice(R.string.scan_failed, detail = e.message)
            } finally {
                scanning = false
            }
        }
    }

    /** [found] in the order set in Settings, reading first the dates a date order needs. */
    private suspend fun ordered(found: List<ImageEntry>): List<ImageEntry> {
        val order = AppSettings.queueOrder.value
        if (order.byDate) readDates(found)
        return sortQueue(found, order) { takenAtCache.getValue(it.file.path) }
    }

    /**
     * Fills [takenAtCache] for every entry not in it yet, with progress: it
     * means opening each file, which on thousands of them takes a while.
     */
    private suspend fun readDates(entries: List<ImageEntry>) {
        val missing = entries.filterNot { takenAtCache.containsKey(it.file.path) }
        if (missing.isEmpty()) return
        val done = AtomicInteger(0)
        datesRead = 0
        datesTotal = missing.size
        readingDates = true
        try {
            coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        datesRead = done.get()
                        delay(PROGRESS_POLL_MS)
                    }
                }
                missing.map { entry ->
                    async(dateDispatcher) {
                        takenAtCache[entry.file.path] = takenAt(entry.file)
                        done.incrementAndGet()
                    }
                }.awaitAll()
                ticker.cancel()
            }
        } finally {
            readingDates = false
        }
    }

    /** Every place [index] moves during browsing goes through here, so the
     * saved position stays current for [start] to pick back up next launch. */
    private fun moveTo(newIndex: Int) {
        index = newIndex
        if (AppSettings.rememberPosition.value) {
            AppSettings.setLastPosition(getApplication(), index, current?.file?.path)
        }
    }

    /** One photo forward — pure navigation, nothing on disk changes. */
    fun next() {
        // Busy: the buttons are off then, but a swipe calls straight in here.
        if (images.isEmpty() || busy) return
        moveTo((index + 1).coerceAtMost(images.lastIndex))
    }

    /** One photo back — pure navigation, nothing on disk changes. */
    fun previous() {
        // Busy: the buttons are off then, but a swipe calls straight in here.
        if (images.isEmpty() || busy) return
        moveTo((index - 1).coerceAtLeast(0))
    }

    /** Jumps back to the first photo and forgets the saved position, from the Settings "reset" row. */
    fun resetPosition() {
        AppSettings.setLastPosition(getApplication(), 0, null)
        if (images.isNotEmpty()) index = 0
    }

    /** Moves the current photo to the trash and drops it out of the queue. */
    fun trash() {
        val entry = current ?: return
        val r = volumeRoot ?: return
        val at = index
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            if (trashEntry(entry, at, r)) {
                moveTo(at.coerceAtMost((images.size - 1).coerceAtLeast(0)))
            } else {
                notice = Notice(R.string.delete_failed)
            }
        }
    }

    /**
     * Moves [entry], at [at] in the queue or -1 when it is not in it, to the
     * trash of [r], its RAW with it, takes it out of the queue and records
     * the step. False if it did not go.
     */
    private suspend fun trashEntry(entry: ImageEntry, at: Int, r: File, batch: Long = 0L): Boolean {
        // Measured before the move: afterwards the files are under other names.
        val (companions, size, trashId) = withContext(Dispatchers.IO) {
            val companions = companionsOf(entry.file)
            Triple(companions, entry.file.length() + companions.sumOf { it.length() }, Trash.moveToTrash(entry, r, companions))
        }
        if (trashId == null) return false
        // Only where it was: the trash is a hidden folder, which the index leaves out.
        announce(listOf(entry.file) + companions)
        if (at >= 0) images = images.toMutableList().also { it.removeAt(at) }
        history.addLast(Step.Trashed(at, entry, trashId, size, batch))
        sessionTrashedCount++
        sessionTrashedBytes += size
        return true
    }

    /**
     * Turns the current photo a quarter clockwise, then hands its path to
     * [onTurned] — for whatever kept a picture of it as it was. No undo: three
     * more turns are one.
     */
    fun rotate(onTurned: (String) -> Unit) {
        val entry = current ?: return
        runBusy(onFailure = Notice(R.string.rotate_failed)) {
            if (withContext(Dispatchers.IO) { rotateClockwise(entry.file) }) {
                announce(listOf(entry.file))
                rotations++
                onTurned(entry.file.path)
            } else {
                notice = Notice(R.string.rotate_failed)
            }
        }
    }

    /** Sorts the current photo, its RAW with it, into [folder] beside it and drops it out of the queue. */
    fun moveInto(folder: SortFolder) {
        val entry = current ?: return
        val at = index
        runBusy(onFailure = Notice(R.string.move_failed)) {
            val (files, movedTo) = withContext(Dispatchers.IO) {
                val files = listOf(entry.file) + companionsOf(entry.file)
                files to SortMove.moveInto(files, folder)
            }
            if (movedTo != null) {
                announce(files + movedTo)
                images = images.toMutableList().also { it.removeAt(at) }
                history.addLast(Step.Moved(at, entry, files, movedTo))
                moveTo(at.coerceAtMost((images.size - 1).coerceAtLeast(0)))
            } else {
                notice = Notice(R.string.move_failed)
            }
        }
    }

    /** Takes back the last photo taken out of the queue — trashed or sorted — and puts it back where it stood. */
    fun undo() {
        if (busy) return
        when (val step = history.removeLastOrNull() ?: return) {
            is Step.Trashed -> {
                // The whole batch it went in with, latest first: the order single undos would take.
                val steps = mutableListOf(step)
                while (step.batch != 0L && (history.lastOrNull() as? Step.Trashed)?.batch == step.batch) {
                    steps += history.removeLast() as Step.Trashed
                }
                undoTrashed(steps)
            }
            is Step.Moved -> undoMoved(step)
        }
    }

    /** Takes [steps] back out of the trash, latest first, each into the spot it left. */
    private fun undoTrashed(steps: List<Step.Trashed>) {
        val r = volumeRoot ?: return
        runBusy(onFailure = Notice(R.string.restore_failed)) {
            val retry = mutableListOf<Step.Trashed>()
            for (step in steps) {
                // Null: the item is no longer in the trash at all.
                val item = withContext(Dispatchers.IO) { Trash.get(r, step.trashId) }
                val result = item?.let { withContext(Dispatchers.IO) { Trash.restore(it) } }
                when (result) {
                    Trash.RestoreResult.OK -> {
                        announce(item?.originals.orEmpty())
                        uncount(step)
                        putBack(step)
                    }
                    // Worth another try, so the step goes back on top.
                    Trash.RestoreResult.FAILED -> {
                        retry += step
                        notice = Notice(R.string.restore_failed)
                    }
                    // Would fail the same way every time and block every undo
                    // before it: dropped. The item itself stays in the trash.
                    Trash.RestoreResult.TARGET_EXISTS -> notice = Notice(R.string.restore_exists)
                    null -> notice = Notice(R.string.restore_failed)
                }
            }
            retry.asReversed().forEach(history::addLast)
        }
    }

    private fun undoMoved(step: Step.Moved) {
        runBusy(onFailure = Notice(R.string.move_failed)) {
            when (withContext(Dispatchers.IO) { SortMove.moveBack(step.movedTo, step.movedFrom) }) {
                SortMove.BackResult.OK -> {
                    announce(step.movedTo + step.movedFrom)
                    putBack(step)
                }
                SortMove.BackResult.FAILED -> {
                    history.addLast(step)
                    notice = Notice(R.string.move_failed)
                }
                // The name is taken where it came from: the file stays sorted,
                // and the step goes, so it cannot block the undos before it.
                SortMove.BackResult.TARGET_EXISTS -> notice = Notice(R.string.restore_exists)
            }
        }
    }

    /** [step]'s photo back into the queue at the spot it left, and on screen — unless it was never in it. */
    private fun putBack(step: Step) {
        if (step.index < 0) return
        images = images.toMutableList().also { it.add(step.index.coerceIn(0, it.size), step.entry) }
        moveTo(step.index.coerceIn(0, images.lastIndex))
    }

    // ---------- Year folders ----------

    /**
     * Works out what a sort into year folders would do — reading every date
     * it needs, with progress — and puts it up as [yearPlan] for an OK.
     */
    fun planYearSort() {
        val r = root ?: return
        if (busy || scanning) return
        busy = true
        yearSortJob = viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.IO) { YearSort.groups(r) }
                val thisYear = currentYear()
                val plan = YearSort.plan(groups, readYears(groups, thisYear), thisYear)
                if (plan.moves.isEmpty()) notice = Notice(R.string.years_nothing) else yearPlan = plan
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = Notice(R.string.scan_failed, detail = e.message)
            } finally {
                yearSortPhase = null
                busy = false
            }
        }
    }

    /** Each of [groups]' year, a few files at a time: it means opening them. */
    private suspend fun readYears(groups: List<List<File>>, thisYear: Int): List<Int?> {
        val done = AtomicInteger(0)
        yearSortDone = 0
        yearSortTotal = groups.size
        yearSortPhase = YearSortPhase.READING
        return coroutineScope {
            val ticker = launch {
                while (isActive) {
                    yearSortDone = done.get()
                    delay(PROGRESS_POLL_MS)
                }
            }
            val years = groups.map { group ->
                async(dateDispatcher) {
                    YearSort.yearOf(group, thisYear, ::recordedAt).also { done.incrementAndGet() }
                }
            }.awaitAll()
            ticker.cancel()
            years
        }
    }

    fun dismissYearPlan() {
        yearPlan = null
    }

    /**
     * Carries out [yearPlan], then scans again: every moved photo is under a
     * new path. The photo on screen stays on screen if it stayed where it
     * was; if it went into a year, the queue goes on with the first file
     * still at the top — the year folders are done, not the next to go through.
     */
    fun sortIntoYears() {
        val plan = yearPlan ?: return
        val r = root ?: return
        yearPlan = null
        if (busy) return
        busy = true
        val onScreen = current?.file
        yearSortJob = viewModelScope.launch {
            val done = AtomicInteger(0)
            var moved = 0
            var failed = 0
            var crashed = false
            val changed = mutableListOf<File>()
            yearSortDone = 0
            yearSortTotal = plan.filesByYear.values.sum()
            yearSortPhase = YearSortPhase.MOVING
            val ticker = launch {
                while (isActive) {
                    yearSortDone = done.get()
                    delay(PROGRESS_POLL_MS)
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    for ((year, groups) in plan.moves) {
                        for (group in groups) {
                            ensureActive()
                            val to = YearSort.moveGroup(group, r, year)
                            if (to == null) {
                                failed += group.size
                            } else {
                                moved += group.size
                                changed += group + to
                                // Same file, same date: no need to open it again under its new name.
                                for ((from, now) in group.zip(to)) {
                                    takenAtCache.remove(from.path)?.let { takenAtCache[now.path] = it }
                                }
                            }
                            done.addAndGet(group.size)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashed = true
                notice = Notice(R.string.move_failed, detail = e.message)
            } finally {
                ticker.cancel()
                announce(changed)
                yearSortPhase = null
                busy = false
                if (!crashed) {
                    notice = if (failed == 0) {
                        Notice(R.string.years_done, args = listOf(formatCount(moved)))
                    } else {
                        Notice(R.string.years_partial, args = listOf(formatCount(moved), formatCount(failed)))
                    }
                }
                rescan { queue ->
                    queue.indexOfFirst { it.file == onScreen }.takeIf { it >= 0 }
                        ?: queue.indexOfFirst { it.relativePath.isEmpty() }.takeIf { it >= 0 }
                        ?: 0
                }
            }
        }
    }

    /** Stops a sort into year folders between two files: what has moved stays moved, and nothing is split. */
    fun cancelYearSort() {
        yearSortJob?.cancel()
    }

    // ---------- Similar shots ----------

    /**
     * Opens the similar-shots screen and looks for groups among the photos in
     * the queue: when each was taken, by the camera's own record — a file's
     * last change says nothing about a burst — then a look at those taken
     * within [SIMILAR_GAP_MS] of another. Videos stay out.
     */
    fun openSimilar() {
        if (busy || scanning || images.isEmpty()) return
        val photos = images.filterNot { isVideo(it.file.path) }
        searchGroups(GroupKind.SIMILAR, none = R.string.similar_none) {
            val taken = readEach(SimilarPhase.READING_DATES, photos) { recordedAt(it.file) }
            val dated = photos.zip(taken).mapNotNull { (entry, at) -> at?.let { entry to it } }
            val runs = timeRuns(dated, SIMILAR_GAP_MS)
            val candidates = runs.flatten()
            val grids = candidates
                .zip(readEach(SimilarPhase.COMPARING, candidates) { visualGrid(it.file) })
                .toMap()
            runs.flatMap { similarGroups(it, grids::get, SIMILAR_MAX_DIFFERENCE) }
        }
    }

    /**
     * Opens the similar-shots screen on copies: every photo and video on the
     * volume the folder is on, not just under the folder — a copy is mostly
     * somewhere else, a messenger's folder or a backup. Folders photos are
     * sorted into are searched too; only hidden ones, the trash among them,
     * stay out. Each group starts with the copy [Duplicates.keeper] picks.
     */
    fun openDuplicates() {
        val volume = volumeRoot ?: return
        if (busy || scanning) return
        searchGroups(GroupKind.DUPLICATES, none = R.string.duplicates_none) {
            similarPhase = SimilarPhase.SEARCHING
            val files = withContext(Dispatchers.IO) {
                ImageScanner.scan(volume, ImageScanner.Progress(), subfolders = true, sortFolders = true) { ensureActive() }
            }
            val groups = Duplicates.confirm(Duplicates.candidates(files.map { it.file })) { copies, hash ->
                readEach(SimilarPhase.COMPARING, copies, hash)
            }
            // The queue's own entry when it has one, so a trashed copy leaves the queue too.
            val queued = images.associateBy { it.file }
            groups.map { group ->
                val keeper = Duplicates.keeper(group)
                (listOf(keeper) + (group.copies - keeper).sortedBy { it.file.path }).map { copy ->
                    queued[copy.file]
                        ?: ImageEntry(copy.file, copy.file.parentFile?.let { pathOnVolume(it, volume) }.orEmpty())
                }
            }
        }
    }

    /** Opens the similar-shots screen on the groups of [kind] that [search] finds, or says [none] were found. */
    private fun searchGroups(kind: GroupKind, @StringRes none: Int, search: suspend () -> List<List<ImageEntry>>) {
        groupKind = kind
        similarGroups = emptyList()
        similarIndex = 0
        similarOpen = true
        similarJob = viewModelScope.launch {
            try {
                val groups = search()
                if (groups.isEmpty()) {
                    similarOpen = false
                    notice = Notice(none)
                } else {
                    similarGroups = groups
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                similarOpen = false
                notice = Notice(R.string.scan_failed, detail = e.message)
            } finally {
                similarPhase = null
            }
        }
    }

    /** [read] of each of [items], a few at a time — each opens a file — with progress under [phase]. */
    private suspend fun <T, R> readEach(phase: SimilarPhase, items: List<T>, read: (T) -> R): List<R> {
        val done = AtomicInteger(0)
        similarDone = 0
        similarTotal = items.size
        similarPhase = phase
        return coroutineScope {
            val ticker = launch {
                while (isActive) {
                    similarDone = done.get()
                    delay(PROGRESS_POLL_MS)
                }
            }
            val results = items.map { item -> async(dateDispatcher) { read(item).also { done.incrementAndGet() } } }
                .awaitAll()
            ticker.cancel()
            results
        }
    }

    fun closeSimilar() {
        similarJob?.cancel()
        similarOpen = false
        similarPhase = null
        similarGroups = emptyList()
    }

    /** On to the next group; past the last one the screen closes. */
    fun nextSimilarGroup() {
        if (similarIndex < similarGroups.lastIndex) {
            similarIndex++
        } else {
            closeSimilar()
            notice = Notice(R.string.similar_done)
        }
    }

    /**
     * Throws out every shot of the group on screen but [keep], by path, in
     * one step that one undo takes back, then goes on to the next group. The
     * photo on the main screen stays there if it was kept.
     */
    fun trashSimilarRest(keep: Set<String>) {
        val group = similarGroups.getOrNull(similarIndex) ?: return
        val r = volumeRoot ?: return
        val out = group.filterNot { it.file.path in keep }
        if (out.isEmpty()) {
            nextSimilarGroup()
            return
        }
        val batch = System.nanoTime()
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            val onScreen = current
            var failed = false
            for (entry in out) {
                // Looked up each time: every one thrown out shifts those after it.
                // A copy found elsewhere on the volume is not in the queue at all.
                val at = images.indexOfFirst { it.file == entry.file }
                if (!trashEntry(images.getOrNull(at) ?: entry, at, r, batch)) failed = true
            }
            val stay = images.indexOf(onScreen)
            moveTo(if (stay >= 0) stay else index.coerceAtMost((images.size - 1).coerceAtLeast(0)))
            if (failed) notice = Notice(R.string.delete_failed)
            nextSimilarGroup()
        }
    }

    fun noticeShown() {
        notice = null
    }

    /**
     * Space on the volume the current folder lives on, or the main storage
     * volume before one is picked — and what that volume's trash holds, which
     * is space that comes back only once the trash is emptied.
     */
    fun openDiskSpace() {
        val path = root?.path ?: Environment.getExternalStorageDirectory().path
        val r = volumeRoot
        viewModelScope.launch {
            val stat = StatFs(path)
            // Listed off the main thread: every entry is a record file to read.
            val trash = if (r == null) null else withContext(Dispatchers.IO) { Trash.list(r) }
            diskTotalBytes = stat.totalBytes
            diskFreeBytes = stat.availableBytes
            trashCount = trash?.size
            trashBytes = trash?.sumOf { it.size } ?: 0L
            diskSpaceOpen = true
        }
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
        val r = volumeRoot ?: return
        viewModelScope.launch {
            trashEntries = withContext(Dispatchers.IO) { Trash.list(r) }
        }
    }

    /** [slot]'s photo is out of the trash again: it no longer counts as thrown out this session. */
    private fun uncount(slot: Step.Trashed) {
        sessionTrashedCount--
        sessionTrashedBytes -= slot.size
    }

    /**
     * The trash-screen entry may be the very photo [undo] on the swipe screen
     * would otherwise restore; dropping its slot here keeps that undo from
     * acting on a photo that already moved or is gone.
     */
    private fun forgetTrashedSlotOf(id: String) {
        history.removeAll { it is Step.Trashed && it.trashId == id }
    }

    fun restoreFromTrash(entry: Trash.Entry) {
        runBusy(onFailure = Notice(R.string.restore_failed)) {
            val result = withContext(Dispatchers.IO) { Trash.restore(entry) }
            notice = Notice(
                when (result) {
                    Trash.RestoreResult.OK -> R.string.restored
                    Trash.RestoreResult.TARGET_EXISTS -> R.string.restore_exists
                    Trash.RestoreResult.FAILED -> R.string.restore_failed
                },
            )
            if (result == Trash.RestoreResult.OK) {
                announce(entry.originals)
                val slot = trashed.firstOrNull { it.trashId == entry.id }
                forgetTrashedSlotOf(entry.id)
                if (slot != null) {
                    uncount(slot)
                    // A copy that was never in the queue goes back to its folder only.
                    if (slot.index >= 0) {
                        // Spliced in ahead of the photo on screen, it would shift
                        // that one along and show its neighbour on the way back.
                        val onScreen = current
                        images = images.toMutableList().also { it.add(slot.index.coerceIn(0, it.size), slot.entry) }
                        moveTo(if (onScreen == null) 0 else images.indexOf(onScreen))
                    }
                }
                reloadTrash()
            }
        }
    }

    fun purgeFromTrash(entry: Trash.Entry) {
        runBusy(onFailure = Notice(R.string.delete_failed)) {
            val ok = withContext(Dispatchers.IO) { Trash.purge(entry) }
            if (ok) {
                forgetTrashedSlotOf(entry.id)
            } else {
                notice = Notice(R.string.delete_failed)
            }
            reloadTrash()
        }
    }

    fun emptyTrash() {
        val r = volumeRoot ?: return
        runTrashJob(TrashJob.EMPTY, onFailure = Notice(R.string.delete_failed)) { progress ->
            val ok = withContext(Dispatchers.IO) { Trash.empty(r, progress) }
            if (ok) {
                history.removeAll { it is Step.Trashed }
            } else {
                notice = Notice(R.string.delete_failed)
            }
        }
    }

    /**
     * Puts the whole trash back. A photo trashed this session goes back into
     * the queue too, where it left it — latest first, the order a run of
     * [undo]s would take — and the one on screen stays on screen.
     */
    fun restoreAllFromTrash() {
        val r = volumeRoot ?: return
        runTrashJob(TrashJob.RESTORE, onFailure = Notice(R.string.restore_failed)) { progress ->
            val result = withContext(Dispatchers.IO) { Trash.restoreAll(r, progress) }
            announce(result.restoredFiles)
            val restored = result.restoredIds.toSet()
            val onScreen = current
            val queue = images.toMutableList()
            for (slot in trashed.reversed()) {
                if (slot.trashId in restored) {
                    uncount(slot)
                    if (slot.index >= 0) queue.add(slot.index.coerceIn(0, queue.size), slot.entry)
                }
            }
            history.removeAll { it is Step.Trashed && it.trashId in restored }
            images = queue
            if (onScreen != null) moveTo(queue.indexOf(onScreen).coerceAtLeast(0))
            notice = if (result.notRestored == 0) {
                Notice(R.string.restored, formatCount(restored.size))
            } else {
                Notice(
                    R.string.restore_all_partial,
                    args = listOf(formatCount(restored.size), formatCount(result.notRestored)),
                )
            }
        }
    }

    /** One whole-trash [job] under [runBusy], its progress polled into [trashJobDone]/[trashJobTotal]. */
    private fun runTrashJob(job: TrashJob, onFailure: Notice, block: suspend (Trash.Progress) -> Unit) {
        runBusy(onFailure) {
            val progress = Trash.Progress()
            trashJobDone = 0
            trashJobTotal = trashEntries?.size ?: 0
            trashJob = job
            val ticker = viewModelScope.launch {
                while (isActive) {
                    trashJobDone = progress.done.get()
                    if (progress.total > 0) trashJobTotal = progress.total
                    delay(PROGRESS_POLL_MS)
                }
            }
            try {
                block(progress)
            } finally {
                ticker.cancel()
                // Not the list from before the job: its files have moved, so
                // until the reload lands it would show rows with no photos.
                trashEntries = null
                trashJob = null
                reloadTrash()
            }
        }
    }
}
