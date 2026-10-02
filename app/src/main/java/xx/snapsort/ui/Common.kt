package xx.snapsort.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xx.snapsort.ACCENT_COUNT
import xx.snapsort.Notice
import xx.snapsort.SortIcon
import xx.snapsort.R
import xx.snapsort.ThemeMode
import xx.snapsort.isVideo
import xx.snapsort.takenAt
import java.io.File
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ---------- Theme ----------

// The window is a distinct tone from the containers, so dialogs and cards stand
// out against the screen behind them. The same values as in the other apps
// built on this skeleton.
val WindowLight = Color(0xFFF1F2F4)
val WindowDark = Color(0xFF121212)
private val TonalButtonDark = Color(0xFF5A5A5A)

// Material3's baseline `error` is a soft, low-chroma rose in the dark scheme
// (meant for text on a dark surface, not a button fill) — nowhere near as
// loud as the trash button needs to read. Same red as the accent palette's
// own "red" choice, so it stays a proper red in both themes.
private val ErrorRed = Color(0xFFE53935)

private val LightColors = lightColorScheme(
    background = WindowLight,
    surface = WindowLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    background = WindowDark,
    surface = WindowDark,
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF1F1F1F),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF303030),
    // Tonal buttons must read as buttons against the dark window.
    secondaryContainer = TonalButtonDark,
    onSecondaryContainer = Color.White,
    error = ErrorRed,
    onError = Color.White,
)

/**
 * Accent choices, the same six as in the other apps built on this skeleton:
 * mid-tones that hold contrast on both windows and carry white text as a
 * button fill.
 */
val AccentPalette = listOf(
    Color(0xFF00897B), // teal
    Color(0xFF1E88E5), // blue
    Color(0xFF5C6BC0), // indigo
    Color(0xFF8E24AA), // purple
    Color(0xFFEF6C00), // orange
    Color(0xFFE53935), // red
).also { check(it.size == ACCENT_COUNT) }

fun accentAt(index: Int): Color = AccentPalette[index.coerceIn(AccentPalette.indices)]

/** Free of Compose, so the activity can ask it before the first frame. */
fun isDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SnapSortTheme(themeMode: ThemeMode, accentIndex: Int, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode, isSystemInDarkTheme())
    val scheme = (if (dark) DarkColors else LightColors).copy(
        primary = accentAt(accentIndex),
        onPrimary = Color.White,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- Text ----------

/** A size the way the system writes it, in the user's units. */
fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** "Label: value", the one way a label and its value are joined. */
fun labelValue(label: String, value: Any): String = "$label: $value"

/** Parts of one line with the one separator between them; empty parts are left out. */
fun dotted(vararg parts: String?): String = parts.filterNot { it.isNullOrEmpty() }.joinToString("  ·  ")

fun noticeText(context: Context, notice: Notice): String {
    val head = context.getString(notice.text, *notice.args.toTypedArray())
    return if (notice.detail.isNullOrBlank()) head else labelValue(head, notice.detail)
}

// ---------- Small controls ----------

/** An icon button smaller than Material's 48dp, for rows that must stay low. */
val COMPACT_BUTTON = 40.dp

/** An icon button a size up from Material's 40dp: the full 48dp it takes up for touch anyway, so a row of them is
 * no wider for it — with a glyph to match. */
val LARGE_BUTTON = 48.dp
val LARGE_ICON = 28.dp

/**
 * An icon button with a round dark backdrop, for when it sits over a photo
 * of unknown colour rather than a plain surface it would otherwise
 * disappear against.
 */
@Composable
fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(LARGE_BUTTON),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ),
    ) {
        Icon(icon, contentDescription, Modifier.size(LARGE_ICON))
    }
}

/**
 * An icon button in the app's inverse-surface pair — dark in a light theme,
 * light in a dark one, always the opposite of the window behind it — for a
 * control over a photo that should read as a solid button rather than a
 * translucent overlay.
 */
@Composable
fun InverseIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(LARGE_BUTTON),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    ) {
        Icon(icon, contentDescription, Modifier.size(LARGE_ICON))
    }
}

/** The ⋮ button that opens an [AppMenu]. [overlay] uses [OverlayIconButton] for when it sits over a photo. */
@Composable
fun MoreButton(onClick: () -> Unit, enabled: Boolean = true, overlay: Boolean = false) {
    if (overlay) {
        OverlayIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_options), onClick, enabled)
    } else {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
        }
    }
}

/** The glyph on a sort folder's button, and on its row in Settings. */
fun SortIcon.vector(): ImageVector = when (this) {
    SortIcon.THUMB_UP -> Icons.Filled.ThumbUp
    SortIcon.DOCUMENT -> Icons.Filled.Description
    SortIcon.STAR -> Icons.Filled.Star
    SortIcon.FAVORITE -> Icons.Filled.Favorite
    SortIcon.PEOPLE -> Icons.Filled.People
    SortIcon.SCHEDULE -> Icons.Filled.Schedule
    SortIcon.SEND -> Icons.AutoMirrored.Filled.Send
    SortIcon.FLIGHT -> Icons.Filled.Flight
    SortIcon.PRINT -> Icons.Filled.Print
    SortIcon.MOOD -> Icons.Filled.Mood
    SortIcon.ARCHIVE -> Icons.Filled.Archive
}

// ---------- Dialog pieces ----------

private val DIALOG_BUTTON_PADDING = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Composable
fun DialogConfirmButton(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = DIALOG_BUTTON_PADDING,
        colors = if (danger) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
fun DialogDismissButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = DIALOG_BUTTON_PADDING) {
        Text(text, maxLines = 1)
    }
}

/**
 * Every drop-down menu in the app: a raised card with an outline, so it
 * stands off the screen behind it in both themes.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        containerColor = scheme.surfaceContainerHighest,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, scheme.outline),
        content = content,
    )
}

/** A dialog's body text: a size up from Material's 14sp, since it is what the dialog is there to say. */
@Composable
private fun dialogBodyStyle() = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)

/**
 * OK/Cancel before a step worth a second thought: the message says what is
 * about to happen, so the buttons can stay the same everywhere. [danger]
 * paints OK red, for a step that cannot be undone.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        // Scrolls: a message listing one line per year can outgrow the screen.
        text = {
            Text(message, style = dialogBodyStyle(), modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { DialogConfirmButton(stringResource(R.string.ok), danger = danger, onClick = onConfirm) },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

/** Single choice from a labelled list; scrolls, since the language list can grow. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onPick(option) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onPick(option) })
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

// A fixed green/red pair, not the theme's accent or error colours: this bar
// reads as a status meter (free vs. used), which should stay the same
// regardless of which accent the user has picked.
private val DiskFreeColor = Color(0xFF43A047)
private val DiskUsedColor = Color(0xFFE53935)

/** A used/free meter, filling from the start the way every storage meter
 * does: [usedFraction] red, the free remainder green. Weights only need to
 * stay in ratio, not sum to 1, so a fraction of exactly 0 or 1 is nudged off
 * zero rather than special-cased, since [Modifier.weight] rejects zero. */
@Composable
fun DiskSpaceBar(usedFraction: Float, modifier: Modifier = Modifier) {
    val used = usedFraction.coerceIn(0f, 1f)
    Row(modifier.height(20.dp).clip(RoundedCornerShape(10.dp))) {
        Box(Modifier.weight(used.coerceAtLeast(0.001f)).fillMaxHeight().background(DiskUsedColor))
        Box(Modifier.weight((1f - used).coerceAtLeast(0.001f)).fillMaxHeight().background(DiskFreeColor))
    }
}

/**
 * Used/total space on the volume a folder lives on, as text plus a [DiskSpaceBar];
 * then what that volume's trash holds ([trashCount] null: no folder, so no trash
 * to speak of) and how much of it this session threw out.
 */
@Composable
fun DiskSpaceDialog(
    freeBytes: Long,
    totalBytes: Long,
    trashCount: Int?,
    trashBytes: Long,
    sessionCount: Int,
    sessionBytes: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val usedBytes = totalBytes - freeBytes
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
    val percentUsed = if (totalBytes > 0) ((usedBytes * 100L) / totalBytes).toInt() else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        // Material's default for a dialog's text slot is onSurfaceVariant —
        // grey on grey. Both numbers here are the point of the dialog, so they
        // get full-contrast onSurface at the same size.
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.disk_space)) },
        text = {
            Column {
                Text(
                    dotted(
                        stringResource(
                            R.string.disk_space_used_of,
                            formatSize(context, usedBytes),
                            formatSize(context, totalBytes),
                        ),
                        stringResource(R.string.disk_space_percent, percentUsed),
                    ),
                    style = dialogBodyStyle(),
                )
                Spacer(Modifier.height(12.dp))
                DiskSpaceBar(usedFraction = usedFraction, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(
                    labelValue(stringResource(R.string.disk_space_free), formatSize(context, freeBytes)),
                    style = dialogBodyStyle(),
                )
                if (trashCount != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        labelValue(stringResource(R.string.disk_space_in_trash), filesAndSize(trashCount, trashBytes)),
                        style = dialogBodyStyle(),
                    )
                }
                if (sessionCount > 0) {
                    Text(
                        labelValue(
                            stringResource(R.string.disk_space_this_session),
                            filesAndSize(sessionCount, sessionBytes),
                        ),
                        style = dialogBodyStyle(),
                    )
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.ok), onDismiss) },
    )
}

/** "137 files  ·  1.2 GB", the count in the language's own plural form. */
@Composable
private fun filesAndSize(count: Int, bytes: Long): String = dotted(
    pluralStringResource(R.plurals.file_count, count, formatCount(count)),
    formatSize(LocalContext.current, bytes),
)

/** One accent colour, ringed when it is the one in force. */
@Composable
fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = color, shape = CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

// ---------- Info pills ----------

/**
 * A translucent rounded chip that reads over a photo of any colour, in either
 * theme. [vertical] is the room above and below the text: a chip of several
 * lines takes what a chip of one line was given, so the buttons over it do not
 * move when a line is added.
 */
@Composable
private fun InfoPill(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    vertical: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = vertical)) { content() }
    }
}

// One bodyLarge character is roughly this wide; used only to decide how much
// of the path to keep before the front-truncating "…", since TextOverflow
// has no built-in start-ellipsis this project can rely on being present.
private const val CHAR_WIDTH_DP = 8.5f

/** [path] cut from the *start* to what fits in [width] of bodyLarge, since the part nearest the file says most. */
private fun cutFromStart(path: String, width: Dp): String {
    val maxChars = (width.value / CHAR_WIDTH_DP).toInt().coerceAtLeast(4)
    return if (path.length > maxChars) "…" + path.takeLast(maxChars - 1) else path
}

/** When a file was taken and how big it is, as [FilePill] shows them. */
private class FileInfo(val takenAt: Long, val size: Long)

/**
 * Everything about the file on screen, in one chip under the photo: its name,
 * when it was taken with how big it is, and the folder it is in. Three lines
 * in the room two chips took, so the buttons above stay where they are. The
 * folder is cut from the *start*, since the part nearest the file says most;
 * the date means opening the file, so that line fills in a moment later.
 */
@Composable
fun FilePill(name: String, path: String, filePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info by produceState<FileInfo?>(initialValue = null, key1 = filePath) {
        // Cleared first: produceState keeps its value across a new key, and the
        // previous photo's date would otherwise stand under this one.
        value = null
        value = withContext(Dispatchers.IO) { File(filePath).let { FileInfo(takenAt(it), it.length()) } }
    }
    InfoPill(modifier, shape = RoundedCornerShape(16.dp), vertical = 1.dp) {
        BoxWithConstraints {
            // Outside the Column: its scope hides BoxWithConstraints' maxWidth.
            val shownPath = cutFromStart(path, maxWidth)
            Column {
                PillLine(name, overflow = TextOverflow.Ellipsis)
                // A blank line, not no line: the chip would otherwise grow by
                // one when the date lands, moving everything above it.
                PillLine(info?.let { dotted(takenText(context, it.takenAt), formatSize(context, it.size)) } ?: " ")
                PillLine(shownPath)
            }
        }
    }
}

/** The folder a file is in, in a chip of one line cut from the start, as [FilePill] shows it. */
@Composable
fun FolderPill(path: String, modifier: Modifier = Modifier) {
    InfoPill(modifier, shape = RoundedCornerShape(16.dp), vertical = 1.dp) {
        BoxWithConstraints { PillLine(cutFromStart(path, maxWidth)) }
    }
}

@Composable
private fun PillLine(text: String, overflow: TextOverflow = TextOverflow.Clip) {
    Text(text, maxLines = 1, overflow = overflow, style = MaterialTheme.typography.bodyLarge)
}

/**
 * The day, month and two-digit year in the language's own order, and the time
 * the way the phone is set to show it, 24-hour or not.
 */
@Composable
private fun takenText(context: Context, takenAt: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "ddMMyy"), takenAt)
    return "$date ${DateFormat.getTimeFormat(context).format(Date(takenAt))}"
}

/** Where the current photo sits in the queue, one-based. */
@Composable
fun CountPill(current: Int, total: Int, modifier: Modifier = Modifier) {
    InfoPill(modifier) {
        Text("$current / $total", maxLines = 1, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------- Photo viewer ----------

private const val MAX_ZOOM = 5f
private val SWIPE_THRESHOLD = 80.dp

private sealed interface PhotoState {
    data object Loading : PhotoState
    data class Loaded(val bitmap: Bitmap) : PhotoState
    data object Failed : PhotoState
}

/**
 * One photo, decoded off a plain file path and downsampled to roughly the
 * space [modifier] gives it. Corrected for its own EXIF orientation, then
 * turned by [rotation] — the device's physical tilt, not the layout's, since
 * every screen that hosts this stays locked portrait; 90/270 swap the frame
 * it is measured against so the photo still fills the screen edge to edge
 * once rotated. Pinch to zoom up to [MAX_ZOOM] and pan while zoomed;
 * [onSwipeForward]/[onSwipeBackward] fire on a plain one-finger drag while it
 * is not, so a caller can page to another photo without this composable
 * knowing what "another photo" means for it — the swipe screen and (in time)
 * the trash screen both show a photo this same way, so a fix to decoding,
 * rotation or zoom only has to happen once. Each photo opens unzoomed unless
 * a [zoom] is passed in: then it is the one zoom for every photo shown with
 * it, so the next opens as close in, on the same spot, as the last was.
 * A new [version] decodes the same path again — the file was changed in place.
 */
@Composable
fun PhotoView(
    path: String,
    version: Int = 0,
    rotation: Int = 0,
    onSwipeForward: () -> Unit = {},
    onSwipeBackward: () -> Unit = {},
    zoom: PhotoZoom? = null,
    modifier: Modifier = Modifier,
) {
    val state = produceState<PhotoState>(initialValue = PhotoState.Loading, key1 = path, key2 = version) {
        value = PhotoState.Loading
        // No timeout: a local file either loads or fails on its own, and a
        // long video's still can take seconds without anything being wrong.
        val bitmap = withContext(Dispatchers.IO) {
            withCancellationSignal { decodeSampled(path, 2048, it) }
        }
        value = if (bitmap != null) PhotoState.Loaded(bitmap) else PhotoState.Failed
    }
    val ownZoom = remember(path) { PhotoZoom() }
    val z = zoom ?: ownZoom
    var scale by z::scale
    var offset by z::offset
    // Read inside the gesture loop, which only restarts on a new path: the
    // phone can turn while one photo stays on screen.
    val currentRotation by rememberUpdatedState(rotation)

    BoxWithConstraints(
        modifier.pointerInput(path) {
            val threshold = SWIPE_THRESHOLD.toPx()
            // One gesture loop for both zoom and swipe, not two detectors side
            // by side: a drag detector consumes the events it handles, and
            // detectTransformGestures abandons a gesture whose events someone
            // else consumed, so a pinch that also tripped the drag's slop was
            // swallowed and the first attempt to zoom did nothing.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var swipeX = 0f
                // Read through the remembered state, not a value captured when
                // this block started: pointerInput only restarts on a new path,
                // so a captured "is it zoomed" would still say no after a zoom.
                var transforming = scale > 1f
                do {
                    val event = awaitPointerEvent()
                    // Something else claimed this gesture; drop it rather than
                    // letting a half-seen drag turn into a swipe.
                    if (event.changes.any { it.isConsumed }) return@awaitEachGesture
                    if (event.changes.size > 1) transforming = true
                    val pan = event.calculatePan()
                    if (transforming) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            if (scale <= 1f) offset = Offset.Zero
                        }
                        if (scale > 1f) {
                            // Only the overhang the zoom created can be panned,
                            // so the photo cannot be dragged off the screen and
                            // stranded there. Re-applied on every event, which
                            // also pulls the photo back as the zoom shrinks.
                            val limitX = size.width * (scale - 1f) / 2f
                            val limitY = size.height * (scale - 1f) / 2f
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-limitX, limitX),
                                (offset.y + pan.y).coerceIn(-limitY, limitY),
                            )
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else {
                        // Along the photo's own width, not the screen's: with
                        // the phone on its side, "left" is what the viewer
                        // sees as left, which is up or down on the glass.
                        swipeX += when (currentRotation) {
                            90 -> pan.y
                            180 -> -pan.x
                            270 -> -pan.y
                            else -> pan.x
                        }
                    }
                } while (event.changes.any { it.pressed })
                if (!transforming) {
                    if (swipeX <= -threshold) onSwipeForward() else if (swipeX >= threshold) onSwipeBackward()
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val turned = rotation == 90 || rotation == 270
        val frame = if (turned) {
            Modifier.requiredSize(width = maxHeight, height = maxWidth)
        } else {
            Modifier.fillMaxSize()
        }
        val video = isVideo(path)
        Box(frame, contentAlignment = Alignment.Center) {
            when (val s = state.value) {
                PhotoState.Loading -> CircularProgressIndicator()
                is PhotoState.Loaded -> Image(
                    bitmap = s.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation.toFloat()
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
                // A video with no still can still be played; the button says enough.
                PhotoState.Failed -> if (!video) Text(stringResource(R.string.photo_load_failed))
            }
        }
        if (video) {
            val context = LocalContext.current
            PlayButton(onClick = { playVideo(context, path) })
        }
    }
}

/**
 * How far a [PhotoView] is zoomed in and where to. Its own for each photo by
 * default; one held outside and passed to several keeps them all alike.
 */
@Stable
class PhotoZoom {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
}

private val PLAY_BUTTON = 80.dp
private val PLAY_ICON = 56.dp

/** The one control a video gets here: playing it is another app's job. */
@Composable
private fun PlayButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(PLAY_BUTTON),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ),
    ) {
        Icon(Icons.Filled.PlayArrow, stringResource(R.string.play_video), Modifier.size(PLAY_ICON))
    }
}

/** Hands the video at [path] to whichever player the user has, through the
 * app's FileProvider: another app cannot read a plain path of ours. */
private fun playVideo(context: Context, path: String) {
    val file = File(path)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "video/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_video_player, Toast.LENGTH_SHORT).show()
    }
}

// ---------- Thumbnails ----------

private const val THUMBNAIL_MAX_DIMENSION = 240
private val THUMBNAIL_PLAY_ICON = 28.dp

/** How far an embedded EXIF preview's proportions may stray from the photo's before
 * it is taken for a letterboxed one, whose black bars a cropped square would show. */
private const val THUMBNAIL_ASPECT_TOLERANCE = 0.02f

/** Thumbnail decodes share a few threads: a fast fling queues one per row it
 * passes, and one still waiting when its row scrolls away is cancelled before
 * it starts, instead of every row's decode running at once. */
private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(3)

/** Decoded thumbnails by path, bounded by bytes, so scrolling back or reopening
 * the trash shows them at once. A trash item's path is unique to its slot. */
/** Drops [path]'s thumbnail, so the next look decodes the file as it is now. */
fun forgetThumbnail(path: String) {
    thumbnailCache.remove(path)
}

private val thumbnailCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** A small square preview of the photo at [path], for a list row — the file [PhotoView] would open full-screen. */
@Composable
fun PhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = thumbnailCache.get(path), key1 = path) {
        value = thumbnailCache.get(path)
            ?: withContext(thumbnailDispatcher) { withCancellationSignal { decodeThumbnail(path, it) } }?.also { thumbnailCache.put(path, it) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (isVideo(path)) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(THUMBNAIL_PLAY_ICON),
            )
        }
    }
}

/**
 * The camera's own preview from the file's EXIF block when it has the photo's
 * proportions — a few kilobytes read instead of the whole photo decoded —
 * otherwise a sampled decode of the photo itself.
 */
private fun decodeThumbnail(path: String, signal: CancellationSignal): Bitmap? {
    if (isVideo(path)) return videoFrame(path, THUMBNAIL_MAX_DIMENSION, signal)
    val exif = readExif(path)
    val embedded = exif?.takeIf { it.hasThumbnail() }?.thumbnailBitmap
    if (embedded != null) {
        val bounds = decodeBounds(path)
        // Both unrotated: the preview is stored the way the sensor wrote the photo.
        if (bounds.outWidth > 0 && bounds.outHeight > 0 && embedded.height > 0) {
            val photoAspect = bounds.outWidth.toFloat() / bounds.outHeight
            val previewAspect = embedded.width.toFloat() / embedded.height
            if (abs(photoAspect - previewAspect) <= photoAspect * THUMBNAIL_ASPECT_TOLERANCE) {
                return upright(embedded, exif)
            }
        }
    }
    return decodeSampled(path, THUMBNAIL_MAX_DIMENSION, signal)
}

private fun decodeBounds(path: String): BitmapFactory.Options =
    BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }

/** A still from the video at [path], fitted within [maxDimension], already
 * upright: the platform applies a video's rotation to the frames it hands out.
 * Anything it cannot read, or a [signal] cancelled mid-way, is a missing
 * still, not a crash. */
private fun videoFrame(path: String, maxDimension: Int, signal: CancellationSignal): Bitmap? = try {
    ThumbnailUtils.createVideoThumbnail(File(path), Size(maxDimension, maxDimension), signal)
} catch (e: Exception) {
    null
}

/**
 * Runs the blocking [block] with a [CancellationSignal] that fires the moment
 * this coroutine is cancelled — a page turned or a row scrolled away — so a
 * call that checks it gives up early instead of finishing for nobody.
 * [BitmapFactory] has no such hook; a video still does.
 */
private suspend fun <T> withCancellationSignal(block: (CancellationSignal) -> T): T = coroutineScope {
    val signal = CancellationSignal()
    // Unconfined and undispatched: the cancel runs on whichever thread cancels
    // this coroutine, not queued behind the very call it is meant to stop on a
    // dispatcher whose few threads that call may be holding.
    val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            signal.cancel()
        }
    }
    try {
        block(signal)
    } finally {
        watcher.cancel()
    }
}

private fun decodeSampled(path: String, maxDimension: Int, signal: CancellationSignal): Bitmap? {
    if (isVideo(path)) return videoFrame(path, maxDimension, signal)
    val bounds = decodeBounds(path)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
    return applyExifRotation(bitmap, path)
}

/** [BitmapFactory] never applies a file's own EXIF orientation; a camera writes a
 * landscape sensor buffer plus this tag rather than rotating the pixels itself. */
private fun applyExifRotation(bitmap: Bitmap, path: String): Bitmap = upright(bitmap, readExif(path))

private fun readExif(path: String): ExifInterface? = try {
    ExifInterface(path)
} catch (e: Exception) {
    // A malformed EXIF block throws more than IOException; the photo is shown unturned.
    null
}

/** [bitmap] turned, and mirrored where the tag says so, the way [exif]'s orientation asks. */
private fun upright(bitmap: Bitmap, exif: ExifInterface?): Bitmap {
    val degrees = exif?.rotationDegrees ?: 0
    val flipped = exif?.isFlipped ?: false
    if (degrees == 0 && !flipped) return bitmap
    val matrix = Matrix().apply {
        // Mirror first, then turn: the library pairs a mirror with the turn that
        // follows it (transpose is 270, transverse 90), and the two do not commute.
        if (flipped) postScale(-1f, 1f)
        postRotate(degrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
