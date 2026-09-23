package xx.snapsort

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Shots further apart than this are never one group. Ten seconds, not five:
 * on a real phone's photos, retakes of one view by hand were up to eight
 * seconds apart, and no two different views that close were alike anyway.
 */
const val SIMILAR_GAP_MS = 10_000L

/**
 * Two frames whose [gridDifference] is above this — an average brightness
 * difference per cell, out of 255 — are different pictures. Set on a real
 * phone's photos: shots of one view by hand came to at most 22.5, the
 * closest two different views to 25.
 */
const val SIMILAR_MAX_DIFFERENCE = 24.0

internal const val GRID_WIDTH = 16
internal const val GRID_HEIGHT = 12

/** The smallest side a photo is decoded at when it carries no preview of its own. */
private const val DECODE_SIDE = 64

/**
 * A picture of [width] × [height] ARGB [pixels] brought down to a 16×12 grey
 * grid, each cell the average brightness of the whole area it covers. An
 * average, not a scaled bitmap: a filtered scale down to a few pixels only
 * looks at a couple of source pixels per cell, and two frames of one view
 * then come out as different as two different views.
 */
internal fun cellLuma(pixels: IntArray, width: Int, height: Int): IntArray {
    val sums = LongArray(GRID_WIDTH * GRID_HEIGHT)
    val counts = IntArray(GRID_WIDTH * GRID_HEIGHT)
    for (y in 0 until height) {
        val row = y * GRID_HEIGHT / height * GRID_WIDTH
        for (x in 0 until width) {
            val c = pixels[y * width + x]
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val b = c and 0xFF
            val cell = row + x * GRID_WIDTH / width
            sums[cell] += (r * 299 + g * 587 + b * 114) / 1000L
            counts[cell]++
        }
    }
    return IntArray(sums.size) { i -> if (counts[i] == 0) 0 else (sums[i] / counts[i]).toInt() }
}

/**
 * How different two [cellLuma] grids are: the average brightness difference
 * per cell, taken where the two line up best with one moved by up to a cell
 * each way — shots by hand shift a little from one to the next, and a view
 * moved by a few percent is still the same view.
 */
internal fun gridDifference(a: IntArray, b: IntArray): Double {
    var best = Double.MAX_VALUE
    for (dy in -1..1) {
        for (dx in -1..1) {
            var sum = 0L
            var n = 0
            for (y in maxOf(0, dy) until minOf(GRID_HEIGHT, GRID_HEIGHT + dy)) {
                for (x in maxOf(0, dx) until minOf(GRID_WIDTH, GRID_WIDTH + dx)) {
                    sum += kotlin.math.abs(a[y * GRID_WIDTH + x] - b[(y - dy) * GRID_WIDTH + (x - dx)])
                    n++
                }
            }
            best = minOf(best, sum.toDouble() / n)
        }
    }
    return best
}

/**
 * [items] with their times, cut into runs where each is at most [gapMs] after
 * the one before; only runs of two or more, in time order. A run is where
 * similar shots can be — nothing outside one is ever compared.
 */
internal fun <T> timeRuns(items: List<Pair<T, Long>>, gapMs: Long): List<List<T>> {
    val runs = mutableListOf<List<T>>()
    var run = mutableListOf<T>()
    var last = Long.MIN_VALUE
    for ((item, time) in items.sortedBy { it.second }) {
        if (run.isNotEmpty() && time - last > gapMs) {
            if (run.size > 1) runs += run
            run = mutableListOf()
        }
        run += item
        last = time
    }
    if (run.size > 1) runs += run
    return runs
}

/**
 * [run] cut where a frame stops looking like the one before it — a chain, so
 * a view that drifts slowly over a series stays one group. A frame with no
 * [grid] (it would not decode) looks like nothing. Only groups of two or more.
 */
internal fun <T> similarGroups(run: List<T>, grid: (T) -> IntArray?, maxDifference: Double): List<List<T>> {
    val groups = mutableListOf<List<T>>()
    var group = mutableListOf<T>()
    var previous: IntArray? = null
    for (item in run) {
        val g = grid(item)
        val alike = g != null && previous != null && gridDifference(g, previous) <= maxDifference
        if (!alike) {
            if (group.size > 1) groups += group
            group = mutableListOf()
        }
        group += item
        previous = g
    }
    if (group.size > 1) groups += group
    return groups
}

/**
 * The [cellLuma] grid of the photo in [file]: from the small preview the
 * camera put in its EXIF, a few kilobytes, or a small decode of the photo
 * when there is none. Not turned upright — frames of one series all lie the
 * same way. Null when neither can be read. Reads the file, so off the main thread.
 */
fun visualGrid(file: File): IntArray? {
    val bitmap = embeddedPreview(file) ?: smallDecode(file) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return cellLuma(pixels, bitmap.width, bitmap.height)
}

private fun embeddedPreview(file: File): Bitmap? = try {
    ExifInterface(file.path).takeIf { it.hasThumbnail() }?.thumbnailBitmap
} catch (e: Exception) {
    // A preview that cannot be read is no preview: the photo itself is decoded instead.
    null
}

private fun smallDecode(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= DECODE_SIDE && bounds.outHeight / (sample * 2) >= DECODE_SIDE) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
