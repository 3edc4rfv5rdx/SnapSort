package xx.snapsort

import androidx.exifinterface.media.ExifInterface
import java.io.File

/** The formats whose EXIF orientation can be written back; HEIC can be read, not saved. */
private val ROTATABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

/**
 * How far a turn moves a file's last-change time: the system's media scanner
 * skips a file whose time and size are what it last saw, and a turn changes
 * neither. Two seconds, not one: FAT on an SD card keeps time in steps of two.
 */
private const val TURN_NUDGE_MS = 2_000L

/**
 * [modified] moved by [TURN_NUDGE_MS], forward from an even step and back from
 * an odd one, so turn after turn it goes back and forth between two times
 * instead of drifting further each time.
 */
internal fun nudged(modified: Long): Long =
    if ((modified / TURN_NUDGE_MS) % 2 == 0L) modified + TURN_NUDGE_MS else modified - TURN_NUDGE_MS

fun canRotate(file: File): Boolean = file.extension.lowercase() in ROTATABLE_EXTENSIONS

/**
 * Turns the photo in [file] a quarter clockwise by its EXIF orientation alone:
 * the pixels are not decoded and encoded again, so nothing is lost. The file
 * keeps its last-change time to within [TURN_NUDGE_MS] ([nudged]), so a turn
 * does not make it look new. False if it could not be written.
 */
fun rotateClockwise(file: File): Boolean {
    if (!canRotate(file)) return false
    val modified = file.lastModified()
    return try {
        ExifInterface(file.path).apply {
            rotate(90)
            saveAttributes()
        }
        file.setLastModified(nudged(modified))
        true
    } catch (e: Exception) {
        // An IOException, or an unsupported format: either way it did not turn.
        false
    }
}
