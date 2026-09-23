package xx.snapsort

import java.io.File
import java.util.Calendar

/** No year before this one is taken as real: a camera with its clock never set writes 1970, 1980 or 2000. */
private const val FIRST_YEAR = 1990

/**
 * The date in a file's name, as cameras and apps write it: `IMG_20190512_…`,
 * `20190512_143012`, `IMG-20190512-WA0001`, `Screenshot_2019-05-12-…`.
 * Not part of a longer run of digits — a timestamp in milliseconds is no
 * date — unless the six digits of a time follow straight on.
 */
private val NAME_DATE = Regex(
    """(?<!\d)((?:19|20)\d{2})[-_.]?(?:0[1-9]|1[0-2])[-_.]?(?:0[1-9]|[12]\d|3[01])""" +
        """(?=\D|$|\d{6}(?!\d))""",
)

/** The year in [name], if it carries a date. */
internal fun yearFromName(name: String): Int? = NAME_DATE.find(name)?.groupValues?.get(1)?.toInt()

/** The year [millis] falls in here: a video's UTC date shot just after midnight on 1 January is in the new year. */
internal fun yearAt(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

/**
 * What [YearSort.plan] found: the groups to move, by year, oldest first, and
 * how many files stay where they are — taken this year, or with no date.
 */
class YearPlan(val moves: Map<Int, List<List<File>>>, val thisYearFiles: Int, val undatedFiles: Int) {
    val filesByYear: Map<Int, Int> get() = moves.mapValues { (_, groups) -> groups.sumOf { it.size } }
}

/**
 * Puts the photos and videos lying at the top of a folder into a folder per
 * year beside them, `2018`, `2019` …, going by when each was taken. Only the
 * top level: what is already in a subfolder was put there by someone. This
 * year's stay out, as do those with no date — a file's last change is not
 * one, it is when it was copied. Plain names, no dash: a year folder is order,
 * not a verdict, so its photos stay in the queue.
 */
object YearSort {
    /**
     * The files at the top of [root] that move together: one name before the
     * extension — a photo, its RAW, a clip of the same name — the photo or
     * video first. Hidden files are left alone: the system keeps its own
     * pending and trashed ones that way.
     */
    fun groups(root: File): List<List<File>> {
        val files = root.listFiles()?.filter {
            it.isFile && !it.name.startsWith(".") && (isMedia(it) || isRaw(it))
        } ?: return emptyList()
        return files.groupBy { it.nameWithoutExtension }.values
            .map { group -> group.sortedWith(compareBy<File> { isRaw(it) }.thenBy { it.name }) }
            .sortedBy { it.first().name }
    }

    /**
     * The year [group] was taken: the camera's record in each file, photo
     * first, then the date in a name. A year after [thisYear] or before 1990
     * is a clock that was wrong, and counts as none.
     */
    fun yearOf(group: List<File>, thisYear: Int, recorded: (File) -> Long?): Int? {
        val sane = FIRST_YEAR..thisYear
        group.firstNotNullOfOrNull { file -> recorded(file)?.let(::yearAt)?.takeIf { it in sane } }?.let { return it }
        return group.firstNotNullOfOrNull { file -> yearFromName(file.name)?.takeIf { it in sane } }
    }

    /** [groups] with the year [years] holds for each, split into what moves and what stays. */
    fun plan(groups: List<List<File>>, years: List<Int?>, thisYear: Int): YearPlan {
        val moves = sortedMapOf<Int, MutableList<List<File>>>()
        var thisYearFiles = 0
        var undatedFiles = 0
        for ((group, year) in groups.zip(years)) {
            when (year) {
                null -> undatedFiles += group.size
                thisYear -> thisYearFiles += group.size
                else -> moves.getOrPut(year) { mutableListOf() }.add(group)
            }
        }
        return YearPlan(moves, thisYearFiles, undatedFiles)
    }

    /**
     * Moves [group] into `[root]/[year]`, made if it is not there yet, all
     * under one name: a taken one gets " (1)", " (2)" … on every file alike,
     * so a photo and its RAW stay a pair. Never over a file already there.
     * Should one file not go, those already moved go back. Returns where each
     * file went, in [group]'s order; null on failure.
     */
    fun moveGroup(group: List<File>, root: File, year: Int): List<File>? =
        SortMove.moveAll(group, File(root, year.toString()))
}
