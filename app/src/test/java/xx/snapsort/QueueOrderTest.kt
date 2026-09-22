package xx.snapsort

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class QueueOrderTest {
    private fun entry(folder: String, name: String) = ImageEntry(File("/pics/$folder/$name"), folder)

    private val a = entry("2023", "b.jpg")
    private val b = entry("2023", "a.jpg")
    private val c = entry("2024", "a.jpg")
    private val d = entry("2024", "z.jpg")

    // A date for each, deliberately out of step with the name order; c and d share one.
    private val dates = mapOf(a to 300L, b to 200L, c to 100L, d to 100L)

    private fun sorted(order: QueueOrder) = sortQueue(listOf(d, c, b, a), order) { dates.getValue(it) }

    @Test
    fun nameOrderGoesFolderByFolderThenByName() {
        assertEquals(listOf(b, a, c, d), sorted(QueueOrder.NAME))
    }

    @Test
    fun dateOrdersCrossFoldersAndBreakTiesByName() {
        assertEquals(listOf(c, d, b, a), sorted(QueueOrder.DATE_OLDEST))
        assertEquals(listOf(a, b, c, d), sorted(QueueOrder.DATE_NEWEST))
    }

    @Test
    fun nameOrderNeverAsksForADate() {
        sortQueue(listOf(a, b), QueueOrder.NAME) { throw AssertionError("date read for a name order") }
    }
}
