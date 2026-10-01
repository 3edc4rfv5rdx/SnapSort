package xx.snapsort

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Finds photos and videos with the same content, reading as little as it can:
 *
 *  1. files are grouped by size, which costs no reading at all;
 *  2. within a group, by a hash of their first and last [EDGE] bytes;
 *  3. only what still matches is hashed whole.
 *
 * Names and dates play no part in finding copies — a copy is often renamed and
 * its date is whatever the app that made it chose — only in [keeper].
 * Carried over from DiskMap, which looks for copies of any file.
 */
object Duplicates {
    /** Smaller files are icons and thumbnails, not photos anybody took. */
    const val MIN_SIZE = 50L * 1024

    /** How much of each end the quick hash reads. */
    private const val EDGE = 64 * 1024

    private const val BLOCK = 1 shl 20

    class Copy(val file: File, val size: Long, val modified: Long) {
        val name: String get() = file.name
    }

    class Group(val size: Long, val copies: List<Copy>)

    /** [files] of at least [minSize] bytes, grouped by size, with lone sizes dropped. Reads no file. */
    fun candidates(files: List<File>, minSize: Long = MIN_SIZE): List<List<Copy>> =
        files.map { Copy(it, it.length(), 0L) }
            .filter { it.size >= minSize }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }

    /**
     * Narrows [candidates] down to real duplicates, most copies first. A file
     * that cannot be read drops out of its group. Every pass of hashes goes
     * through [hashEach] — one hash per copy, in order — so the caller decides
     * how many files are read at once and shows how far it has got.
     */
    suspend fun confirm(
        candidates: List<List<Copy>>,
        hashEach: suspend (List<Copy>, (Copy) -> String?) -> List<String?> = { copies, hash -> copies.map(hash) },
    ): List<Group> {
        val quick = split(candidates) { hashEach(it) { copy -> edgeHash(copy.file, copy.size) } }
        // Files no longer than both edges were read whole already.
        val (whole, partly) = quick.partition { it[0].size <= 2L * EDGE }
        val exact = whole + split(partly) { hashEach(it) { copy -> fullHash(copy.file) } }
        return exact
            .map { group -> Group(group[0].size, group.map { Copy(it.file, it.size, it.file.lastModified()) }) }
            .sortedWith(compareByDescending<Group> { it.copies.size }.thenByDescending { it.size })
    }

    /** Each of [groups] cut by the key [keys] gives its copies; unreadable files (null key) and lone files dropped. */
    private suspend fun split(groups: List<List<Copy>>, keys: suspend (List<Copy>) -> List<String?>): List<List<Copy>> {
        val copies = groups.flatten()
        val byCopy = copies.zip(keys(copies)).toMap()
        return groups.flatMap { group ->
            group.mapNotNull { copy -> byCopy[copy]?.let { it to copy } }
                .groupBy({ it.first }, { it.second })
                .values
                .filter { it.size > 1 }
        }
    }

    private fun edgeHash(file: File, size: Long): String? = try {
        RandomAccessFile(file, "r").use { raf ->
            val digest = MessageDigest.getInstance("SHA-1")
            val buf = ByteArray(EDGE)
            // readFully: a file shorter than it was when listed has changed
            // since, and the EOFException drops it from the group.
            val head = minOf(EDGE.toLong(), size).toInt()
            raf.readFully(buf, 0, head)
            digest.update(buf, 0, head)
            if (size > EDGE) {
                val tailStart = maxOf(EDGE.toLong(), size - EDGE)
                val tail = (size - tailStart).toInt()
                raf.seek(tailStart)
                raf.readFully(buf, 0, tail)
                digest.update(buf, 0, tail)
            }
            digest.digest().toHex()
        }
    } catch (e: IOException) {
        null
    }

    private fun fullHash(file: File): String? = try {
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-1")
            val buf = ByteArray(BLOCK)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
            digest.digest().toHex()
        }
    } catch (e: IOException) {
        null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // ---------- Which copy stays ----------

    // " (1)" at the end, "copy"/"копия"/"копія" at the end with an optional
    // number, or "Copy of"/"Копия" in front — in the name without its extension.
    private val COPY_MARK = Regex(
        """\(\d+\)\s*$|(^|[\s_-])(copy|копия|копія)([\s_-]*\d+)?\s*$|^(copy of|копия|копія)\s""",
        RegexOption.IGNORE_CASE,
    )

    /** True when the name says it is a copy of something. */
    fun looksLikeCopy(name: String): Boolean = COPY_MARK.containsMatchIn(name.substringBeforeLast('.'))

    /** True when [file] lies in a folder photos are sorted into ([SortFolder]): somebody already chose to keep it. */
    private fun isSorted(file: File): Boolean =
        generateSequence(file.parentFile) { it.parentFile }.any { it.name.startsWith(SORT_DIR_PREFIX) }

    /**
     * The copy most likely to be the original: one already sorted into a
     * folder first, then a name without a copy mark, then the oldest, then
     * the shortest path.
     */
    fun keeper(group: Group): Copy = group.copies.minWith(
        compareBy<Copy>({ !isSorted(it.file) }, { looksLikeCopy(it.name) }, { it.modified }, { it.file.path.length }),
    )
}
