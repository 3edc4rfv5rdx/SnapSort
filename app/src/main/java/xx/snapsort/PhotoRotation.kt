package xx.snapsort

import androidx.exifinterface.media.ExifInterface
import java.io.File

/** The formats whose EXIF orientation can be written back; HEIC can be read, not saved. */
private val ROTATABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

fun canRotate(file: File): Boolean = file.extension.lowercase() in ROTATABLE_EXTENSIONS

/**
 * Turns the photo in [file] a quarter clockwise by its EXIF orientation alone:
 * the pixels are not decoded and encoded again, so nothing is lost. The file
 * keeps its last-change time, so a turn does not make it look new. False if
 * it could not be written.
 */
fun rotateClockwise(file: File): Boolean {
    if (!canRotate(file)) return false
    val modified = file.lastModified()
    return try {
        ExifInterface(file.path).apply {
            rotate(90)
            saveAttributes()
        }
        file.setLastModified(modified)
        true
    } catch (e: Exception) {
        // An IOException, or an unsupported format: either way it did not turn.
        false
    }
}
