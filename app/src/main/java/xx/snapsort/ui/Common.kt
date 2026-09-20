package xx.snapsort.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xx.snapsort.ACCENT_COUNT
import xx.snapsort.Notice
import xx.snapsort.R
import xx.snapsort.ThemeMode
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale

// ---------- Theme ----------

// The window is a distinct tone from the containers, so dialogs and cards stand
// out against the screen behind them. The same values as in the other apps
// built on this skeleton.
val WindowLight = Color(0xFFF1F2F4)
val WindowDark = Color(0xFF121212)
private val TonalButtonDark = Color(0xFF5A5A5A)

private val LightColors = lightColorScheme(
    background = WindowLight,
    surface = WindowLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
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
    val head = context.getString(notice.text)
    return if (notice.detail.isNullOrBlank()) head else labelValue(head, notice.detail)
}

// ---------- Small controls ----------

/** An icon button smaller than Material's 48dp, for rows that must stay low. */
val COMPACT_BUTTON = 40.dp

/** The ⋮ button that opens an [AppMenu]. */
@Composable
fun MoreButton(onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
    }
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

/** Yes/no for something that cannot be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { DialogConfirmButton(confirmText, danger = true, onClick = onConfirm) },
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
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

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

/** A translucent rounded chip that reads over a photo of any colour, in either theme. */
@Composable
private fun InfoPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { content() }
    }
}

/** A photo's file name, meant to sit over the top of the photo. */
@Composable
fun NamePill(name: String, modifier: Modifier = Modifier) {
    InfoPill(modifier.widthIn(max = 220.dp)) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
    }
}

private const val PATH_PILL_MAX_CHARS = 40

/** A photo's folder, truncated from the *start* so the part nearest the file stays visible. */
@Composable
fun PathPill(path: String, modifier: Modifier = Modifier) {
    val shown = if (path.length > PATH_PILL_MAX_CHARS) "…" + path.takeLast(PATH_PILL_MAX_CHARS - 1) else path
    InfoPill(modifier) {
        Text(shown, maxLines = 1, overflow = TextOverflow.Clip, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------- Photo viewer ----------

private const val PHOTO_LOAD_TIMEOUT_MS = 3_000L
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
 * rotation or zoom only has to happen once.
 */
@Composable
fun PhotoView(
    path: String,
    rotation: Int = 0,
    onSwipeForward: () -> Unit = {},
    onSwipeBackward: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state = produceState<PhotoState>(initialValue = PhotoState.Loading, key1 = path) {
        value = PhotoState.Loading
        val bitmap = withContext(Dispatchers.IO) {
            withTimeoutOrNull(PHOTO_LOAD_TIMEOUT_MS) { decodeSampled(path, 2048) }
        }
        value = if (bitmap != null) PhotoState.Loaded(bitmap) else PhotoState.Failed
    }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1f

    BoxWithConstraints(
        modifier
            .pointerInput(path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        if (scale <= 1f) offset = Offset.Zero
                    }
                    if (scale > 1f) offset += pan
                }
            }
            .then(
                if (zoomed) {
                    Modifier
                } else {
                    Modifier.pointerInput(path) {
                        val threshold = SWIPE_THRESHOLD.toPx()
                        var accumulated = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (accumulated <= -threshold) onSwipeForward()
                                else if (accumulated >= threshold) onSwipeBackward()
                                accumulated = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val turned = rotation == 90 || rotation == 270
        val frame = if (turned) Modifier.size(width = maxHeight, height = maxWidth) else Modifier.fillMaxSize()
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
                PhotoState.Failed -> Text(stringResource(R.string.photo_load_failed))
            }
        }
    }
}

private fun decodeSampled(path: String, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
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
private fun applyExifRotation(bitmap: Bitmap, path: String): Bitmap {
    val degrees = try {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: IOException) {
        0
    }
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
