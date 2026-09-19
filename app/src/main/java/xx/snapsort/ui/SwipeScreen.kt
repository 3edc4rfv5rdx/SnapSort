package xx.snapsort.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import xx.snapsort.R
import xx.snapsort.SnapSortViewModel

/**
 * The whole app: one photo at a time, kept or trashed with a button tap, with
 * a folder to pick before any of that and a ⋮ menu for the trash, settings
 * and about.
 */
@Composable
fun SwipeScreen(
    vm: SnapSortViewModel,
    onPickFolder: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        when {
            !vm.hasFolder -> EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.pick_folder),
                message = stringResource(R.string.pick_folder_hint),
                action = { PickFolderButton(onPickFolder) },
            )

            vm.scanning -> ScanningState(vm.scannedCount)

            vm.images.isEmpty() -> EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.no_photos),
                message = null,
                action = { PickFolderButton(onPickFolder) },
            )

            vm.finished -> EmptyState(
                icon = Icons.Filled.Check,
                title = stringResource(R.string.all_done),
                message = null,
                action = null,
            )

            else -> vm.current?.let { entry ->
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PhotoView(entry.file.uri, Modifier.fillMaxSize())
                    }
                    BottomBar(
                        canUndo = vm.canUndo,
                        busy = vm.busy,
                        onTrash = vm::trash,
                        onKeep = vm::keep,
                        onUndo = vm::undo,
                    )
                }
            }
        }

        Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.systemBars).padding(4.dp)) {
            MoreButton(onClick = { menuOpen = true })
            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trash)) },
                    onClick = { menuOpen = false; vm.openTrash() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = { menuOpen = false; settingsOpen = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.about)) },
                    onClick = { menuOpen = false; onAbout() },
                )
            }
        }

        vm.notice?.let { notice ->
            val context = LocalContext.current
            LaunchedEffect(notice) {
                delay(2500)
                vm.noticeShown()
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {},
            ) { Text(noticeText(context, notice)) }
        }
    }

    if (vm.trashOpen) {
        FullScreenDialog(title = stringResource(R.string.trash), onDismiss = vm::closeTrash) {
            TrashScreen(
                entries = vm.trashEntries,
                busy = vm.busy,
                onRestore = vm::restoreFromTrash,
                onPurge = vm::purgeFromTrash,
                onEmpty = vm::emptyTrash,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (settingsOpen) {
        FullScreenDialog(title = stringResource(R.string.settings), onDismiss = { settingsOpen = false }) {
            SettingsScreen(modifier = Modifier.weight(1f))
        }
    }
}

/** A dialog that covers the screen, with a title row above whatever [content] puts in its Column. */
@Composable
private fun FullScreenDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                content()
            }
        }
    }
}

@Composable
private fun PickFolderButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(Icons.Filled.FolderOpen, stringResource(R.string.pick_folder))
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String?,
    action: (@Composable () -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
private fun ScanningState(count: Int) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(labelValue(stringResource(R.string.scanning), formatCount(count)))
    }
}

private val BOTTOM_BUTTON = 64.dp

@Composable
private fun BottomBar(canUndo: Boolean, busy: Boolean, onTrash: () -> Unit, onKeep: () -> Unit, onUndo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onTrash,
            enabled = !busy,
            modifier = Modifier.size(BOTTOM_BUTTON),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { Icon(Icons.Filled.Delete, stringResource(R.string.to_trash)) }

        FilledTonalIconButton(
            onClick = onUndo,
            enabled = canUndo && !busy,
            modifier = Modifier.size(BOTTOM_BUTTON),
        ) { Icon(Icons.Filled.Undo, stringResource(R.string.undo)) }

        FilledIconButton(
            onClick = onKeep,
            enabled = !busy,
            modifier = Modifier.size(BOTTOM_BUTTON),
        ) { Icon(Icons.Filled.Check, stringResource(R.string.keep)) }
    }
}

/** A photo loaded from its content Uri, downsampled to roughly the space it is shown in. */
@Composable
private fun PhotoView(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) { decodeSampled(context.contentResolver, uri, 2048) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = bitmapState.value
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun decodeSampled(resolver: ContentResolver, uri: Uri, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}
