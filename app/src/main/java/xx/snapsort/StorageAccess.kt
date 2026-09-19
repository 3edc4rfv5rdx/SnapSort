package xx.snapsort

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

/** Whether this app currently holds "All files access" — every read/write in
 * [ImageScanner]/[Trash] relies on it being granted. */
fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

/** The system settings screen that grants or revokes it. There is no normal
 * runtime permission dialog for this one — it is sensitive enough that the
 * system requires the user to flip it on by hand. */
fun allFilesAccessIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

/**
 * The folder a SAF tree Uri points to, as a plain path. SnapSort keeps the
 * system folder picker (ACTION_OPEN_DOCUMENT_TREE) for its UI, but once
 * [hasAllFilesAccess] is granted, everything else — scanning, reading,
 * moving — goes through this path with plain java.io.File instead of the
 * picker's own Uri.
 *
 * Primary storage only: that is where a device without removable storage
 * keeps DCIM, and it is what this app has been pointed at so far. A volume
 * that is not primary is reported as unsupported rather than guessed at.
 */
fun Uri.treeToFile(): File? = runCatching {
    val treeId = DocumentsContract.getTreeDocumentId(this)
    val colon = treeId.indexOf(':')
    if (colon < 0) return@runCatching null
    val volume = treeId.substring(0, colon)
    if (volume != "primary") return@runCatching null
    val relative = treeId.substring(colon + 1)
    val root = Environment.getExternalStorageDirectory()
    if (relative.isEmpty()) root else File(root, relative)
}.getOrNull()
