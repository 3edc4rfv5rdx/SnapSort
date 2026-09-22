package xx.snapsort

/** The order the queue goes through a folder in. */
enum class QueueOrder {
    /** Folder by folder, each by file name. */
    NAME,

    /** By the date each was taken, across every folder at once. */
    DATE_OLDEST,
    DATE_NEWEST,
    ;

    val byDate: Boolean get() = this != NAME
}

/**
 * [entries] in [order]. [takenAt] is asked only for a date order, and must
 * know every entry by then. Ties — a burst in one second, or files with no
 * date of their own — fall back to the name order, so the queue is the same
 * every time the folder opens.
 */
fun sortQueue(entries: List<ImageEntry>, order: QueueOrder, takenAt: (ImageEntry) -> Long): List<ImageEntry> {
    val byName = compareBy<ImageEntry>({ it.relativePath }, { it.file.name })
    val comparator = when (order) {
        QueueOrder.NAME -> byName
        QueueOrder.DATE_OLDEST -> compareBy<ImageEntry> { takenAt(it) }.then(byName)
        QueueOrder.DATE_NEWEST -> compareByDescending<ImageEntry> { takenAt(it) }.then(byName)
    }
    return entries.sortedWith(comparator)
}
