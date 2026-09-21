package xx.snapsort

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
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

/** A volume the folder picker can start from, named the way the system names it. */
class StorageRoot(val dir: File, val label: String)

/**
 * Every storage volume this app can walk — internal storage, and an SD card
 * when one is in. The app browses these with plain java.io.File under
 * [hasAllFilesAccess] rather than through the system's document picker: the
 * picker asks the user to confirm access to each folder it hands over, every
 * single time, for a grant this app never uses once it has a path.
 */
fun storageRoots(context: Context): List<StorageRoot> =
    context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
        volume.directory?.takeIf { it.isDirectory }?.let { StorageRoot(it, volume.getDescription(context)) }
    }

/** The root of the volume [file] is on — where the trash goes, so moving a
 * photo into it is a rename on the same volume, never a copy. */
fun volumeRootOf(context: Context, file: File): File? =
    context.getSystemService(StorageManager::class.java).getStorageVolume(file)?.directory

/** [dir] as a path inside [volume] — "DCIM/Camera", not
 * "/storage/emulated/0/DCIM/Camera". The full path when [dir] is the volume
 * itself or lies outside it. */
fun pathOnVolume(dir: File, volume: File?): String {
    val prefix = volume?.path?.trimEnd('/')?.plus("/") ?: return dir.path
    return if (dir.path.startsWith(prefix)) dir.path.removePrefix(prefix) else dir.path
}
