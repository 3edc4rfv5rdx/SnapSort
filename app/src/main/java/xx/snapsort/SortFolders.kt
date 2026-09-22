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
     * Moves [file] into its folder's `-<name>` subfolder, made if it is not
     * there yet. Never over a file already there: a taken name gets " (1)",
     * " (2)" … before its extension. Returns where it went, null on failure.
     */
    fun moveInto(file: File, folder: SortFolder): File? {
        val parent = file.parentFile ?: return null
        val dir = File(parent, folder.dirName)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val target = freeName(dir, file.name)
        return target.takeIf { file.renameTo(it) }
    }

    /** Undoes [moveInto]: [movedTo] back to [original], never over something that is there now. */
    fun moveBack(movedTo: File, original: File): BackResult {
        if (original.exists()) return BackResult.TARGET_EXISTS
        return if (movedTo.renameTo(original)) BackResult.OK else BackResult.FAILED
    }

    private fun freeName(dir: File, name: String): File {
        var candidate = File(dir, name)
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (candidate.exists()) candidate = File(dir, "$base (${n++})$ext")
        return candidate
    }
}
