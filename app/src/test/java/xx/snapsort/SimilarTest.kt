package xx.snapsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarTest {
    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    /** A [GRID_WIDTH] × [GRID_HEIGHT] grid of [cell] values. */
    private fun grid(cell: (x: Int, y: Int) -> Int) = IntArray(GRID_WIDTH * GRID_HEIGHT) { i ->
        cell(i % GRID_WIDTH, i / GRID_WIDTH)
    }

    @Test
    fun eachCellIsTheAverageOfTheAreaItCovers() {
        // Twice the grid each way: every cell averages a 2×2 block; one block of
        // 0, 100, 100, 200 averages to 100 whatever its single pixels are.
        val width = GRID_WIDTH * 2
        val pixels = IntArray(width * GRID_HEIGHT * 2) { grey(50) }
        pixels[0] = grey(0)
        pixels[1] = grey(100)
        pixels[width] = grey(100)
        pixels[width + 1] = grey(200)
        val luma = cellLuma(pixels, width, GRID_HEIGHT * 2)
        assertEquals(100, luma[0])
        assertEquals(50, luma[1])
        assertEquals(GRID_WIDTH * GRID_HEIGHT, luma.size)
    }

    @Test
    fun aViewMovedByACellIsTheSameView() {
        val a = grid { x, y -> (x * 37 + y * 91) % 256 }
        val moved = grid { x, y -> ((x + 1) * 37 + y * 91) % 256 }
        assertEquals(0.0, gridDifference(a, a), 0.0)
        assertEquals(0.0, gridDifference(a, moved), 0.0)
    }

    @Test
    fun aDifferentViewIsFarApart() {
        val light = grid { x, _ -> x * 16 }
        val dark = grid { x, _ -> 255 - x * 16 }
        assertTrue(gridDifference(light, dark) > SIMILAR_MAX_DIFFERENCE)
    }

    @Test
    fun runsBreakWhereTheGapIsTooLongAndLoneShotsDropOut() {
        val shots = listOf("c" to 40_000L, "a" to 0L, "b" to 9_000L, "d" to 60_000L, "e" to 69_999L, "f" to 90_000L)
        assertEquals(listOf(listOf("a", "b"), listOf("d", "e")), timeRuns(shots, SIMILAR_GAP_MS))
    }

    @Test
    fun aRunIsCutWhereAFrameStopsLookingLikeTheOneBefore() {
        val light = grid { _, _ -> 200 }
        val lighter = grid { _, _ -> 210 }
        val dark = grid { _, _ -> 20 }
        val grids = mapOf("a" to light, "b" to lighter, "c" to dark, "d" to dark, "e" to light)
        val groups = similarGroups(listOf("a", "b", "c", "d", "e"), grids::get, SIMILAR_MAX_DIFFERENCE)
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), groups)
    }

    @Test
    fun aShotThatWouldNotDecodeBelongsToNoGroup() {
        val flat = grid { _, _ -> 100 }
        val grids = mapOf("a" to flat, "c" to flat)
        assertEquals(emptyList<List<String>>(), similarGroups(listOf("a", "b", "c"), grids::get, SIMILAR_MAX_DIFFERENCE))
    }
}
