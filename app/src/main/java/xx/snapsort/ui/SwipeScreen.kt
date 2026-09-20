package xx.snapsort.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import xx.snapsort.R
import xx.snapsort.SnapSortViewModel
import xx.snapsort.rememberDeviceRotation

/**
 * The whole app: one photo at a time, kept or trashed with a button tap, with
 * a folder to pick before any of that and a ⋮ menu for the trash, settings
 * and about.
 */
@Composable
fun SwipeScreen(
    vm: SnapSortViewModel,
    onPickFolder: () -> Unit,
    onGrantAccess: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        when {
            !vm.hasStorageAccess -> EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.need_storage_access),
                message = stringResource(R.string.need_storage_access_hint),
                action = {
                    Button(onClick = onGrantAccess) { Text(stringResource(R.string.grant_access)) }
                },
            )

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

            else -> vm.viewed?.let { entry ->
                val rotation = rememberDeviceRotation()
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PhotoView(
                            path = entry.file.path,
                            rotation = rotation,
                            onSwipeForward = { if (!vm.busy) vm.browseNext() },
                            onSwipeBackward = { if (!vm.busy) vm.browsePrev() },
                            modifier = Modifier.fillMaxSize(),
                        )
                        NamePill(
                            name = entry.file.name,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(start = 12.dp, top = 12.dp, end = 64.dp),
                        )
                    }
                    BottomBar(
                        canUndo = vm.canUndo,
                        busy = vm.busy || vm.browsingAway,
                        onTrash = vm::trash,
                        onKeep = vm::keep,
                        onUndo = vm::undo,
                    )
                    if (entry.relativePath.isNotBlank()) {
                        PathPill(
                            path = entry.relativePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(horizontal = 16.dp, bottom = 8.dp),
                        )
                    } else {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .height(8.dp),
                        )
                    }
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

/** A dialog that covers the screen, with a back button and title above whatever [content] puts in its Column. */
@Composable
private fun FullScreenDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                content()
            }
        }
    }
}

@Composable
private fun PickFolderButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(88.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.Filled.FolderOpen, stringResource(R.string.pick_folder), modifier = Modifier.size(40.dp))
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
        ) { Icon(Icons.Filled.ThumbDown, stringResource(R.string.to_trash)) }

        FilledIconButton(
            onClick = onUndo,
            enabled = canUndo && !busy,
            modifier = Modifier.size(BOTTOM_BUTTON),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }

        FilledIconButton(
            onClick = onKeep,
            enabled = !busy,
            modifier = Modifier.size(BOTTOM_BUTTON),
        ) { Icon(Icons.Filled.ThumbUp, stringResource(R.string.keep)) }
    }
}
