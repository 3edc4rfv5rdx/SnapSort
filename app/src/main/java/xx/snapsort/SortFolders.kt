package xx.snapsort

import java.io.File

/**
 * One of the folders a photo can be sorted into besides keep and trash: a
 * subfolder of the photo's own folder, `-<name>`, and the icon of its button.
 * The dash marks it as the app's own, the way the dot marks a hidden folder —
 * [ImageScanner] skips both, so sorted photos never come back into the queue,
 * whatever the folder is called now or was called when they went in.
 */
data class SortFolder(val name: String, val icon: SortIcon) {
    val dirName: String get() = SORT_DIR_PREFIX + name
}

/** The icons a [SortFolder]'s button can carry; drawn in the UI layer. */
enum class SortIcon { THUMB_UP, DOCUMENT, STAR, FAVORITE, PEOPLE, SCHEDULE, SEND, FLIGHT, PRINT, MOOD, ARCHIVE }

const val SORT_DIR_PREFIX = "-"
const val MAX_SORT_FOLDERS = 5

val DEFAULT_SORT_FOLDERS = listOf(SortFolder("Best", SortIcon.THUMB_UP), SortFolder("Docs", SortIcon.DOCUMENT))

/**
 * A name as typed into Settings, made fit for a folder: trimmed, no path
 * separator, no dash or dot of its own in front — the dash is added, and a
 * dot would hide the folder. Empty when nothing usable is left.
 */
fun cleanSortName(typed: String): String = typed.replace('/', ' ').trim().trimStart('-', '.').trim()

/** The folders, one per line as `ICON<tab>name`, for a single preferences string. */
fun encodeSortFolders(folders: List<SortFolder>): String = folders.joinToString("\n") { "${it.icon.name}\t${it.name}" }

/** Back from [encodeSortFolders]; a line it cannot read is left out rather than failing the rest. */
fun decodeSortFolders(text: String): List<SortFolder> = text.lines().mapNotNull { line ->
    val icon = SortIcon.entries.firstOrNull { it.name == line.substringBefore('\t') } ?: return@mapNotNull null
    val name = cleanSortName(line.substringAfter('\t', ""))
    if (name.isEmpty()) null else SortFolder(name, icon)
}.distinctBy { it.name }.take(MAX_SORT_FOLDERS)

object SortMove {
    enum class BackResult { OK, TARGET_EXISTS, FAILED }

    /**
     * Moves [files] — a photo and its [companionsOf] — into their folder's
     * `-<name>` subfolder, as [moveAll] does. Returns where each went, in
     * [files]' order; null on failure.
     */
    fun moveInto(files: List<File>, folder: SortFolder): List<File>? {
        val parent = files.firstOrNull()?.parentFile ?: return null
        return moveAll(files, File(parent, folder.dirName))
    }

    /**
     * Moves [files] into [dir], made if it is not there yet, all under one
     * name: a taken one gets " (1)", " (2)" … before the extension, on every
     * file alike, so a photo and its RAW stay a pair. Never over a file
     * already there. Should one file not go, those already moved go back.
     * Returns where each went, in [files]' order; null on failure.
     */
    fun moveAll(files: List<File>, dir: File): List<File>? {
        if (!dir.isDirectory && !dir.mkdirs()) return null
        var n = 0
        var targets: List<File>
        do {
            val suffix = n++
            targets = files.map { File(dir, numbered(it.name, suffix)) }
        } while (targets.any { it.exists() })
        return targets.takeIf { renameAll(files, targets) }
    }

    /** Undoes [moveInto]: [movedTo] back to [original], never over something that is there now. */
    fun moveBack(movedTo: List<File>, original: List<File>): BackResult {
        if (original.any { it.exists() }) return BackResult.TARGET_EXISTS
        return if (renameAll(movedTo, original)) BackResult.OK else BackResult.FAILED
    }

    /** Each of [from] to the same place in [to], all or none: a failure puts back those already moved. */
    internal fun renameAll(from: List<File>, to: List<File>): Boolean {
        val done = mutableListOf<Pair<File, File>>()
        for ((source, target) in from.zip(to)) {
            if (!source.renameTo(target)) {
                done.forEach { (back, now) -> now.renameTo(back) }
                return false
            }
            done.add(source to target)
        }
        return true
    }

    private fun numbered(name: String, n: Int): String {
        if (n == 0) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return "$base ($n)$ext"
    }
}
