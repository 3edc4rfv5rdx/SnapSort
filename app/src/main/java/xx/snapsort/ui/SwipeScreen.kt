package xx.snapsort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        when {
            vm.trashOpen -> AppScreen(
                title = stringResource(R.string.trash),
                onBack = vm::closeTrash,
                actions = {
                    DialogConfirmButton(
                        text = stringResource(R.string.trash_clear_action),
                        danger = true,
                        enabled = !vm.busy && !vm.trashEntries.isNullOrEmpty(),
                    ) { confirmEmptyTrash = true }
                },
            ) {
                TrashScreen(
                    entries = vm.trashEntries,
                    busy = vm.busy,
                    onRestore = vm::restoreFromTrash,
                    onPurge = vm::purgeFromTrash,
                    modifier = Modifier.weight(1f),
                )
            }

            settingsOpen -> AppScreen(title = stringResource(R.string.settings), onBack = { settingsOpen = false }) {
                SettingsScreen(modifier = Modifier.weight(1f))
            }

            else -> {
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

                    else -> vm.current?.let { entry ->
                        val rotation = rememberDeviceRotation()
                        Column(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.statusBars),
                            ) {
                                PhotoView(
                                    path = entry.file.path,
                                    rotation = rotation,
                                    onSwipeForward = vm::next,
                                    onSwipeBackward = vm::previous,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                CountPill(
                                    current = vm.index + 1,
                                    total = vm.images.size,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 12.dp, top = 12.dp, end = 64.dp),
                                )
                            }
                            BottomBar(
                                canGoBack = vm.index > 0,
                                canGoForward = vm.index < vm.images.lastIndex,
                                busy = vm.busy,
                                onTrash = vm::trash,
                                onBack = vm::previous,
                                onForward = vm::next,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                            ) {
                                NamePill(name = entry.file.name, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(2.dp))
                                PathPill(
                                    path = entry.relativePath.ifBlank { "/" },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.systemBars).padding(4.dp)) {
                    MoreButton(onClick = { menuOpen = true }, overlay = true)
                    AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.undo), style = MaterialTheme.typography.titleLarge)
                            },
                            enabled = vm.canUndo,
                            onClick = { menuOpen = false; vm.undo() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.change_folder),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            onClick = { menuOpen = false; onPickFolder() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.trash), style = MaterialTheme.typography.titleLarge)
                            },
                            onClick = { menuOpen = false; vm.openTrash() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
                            },
                            onClick = { menuOpen = false; settingsOpen = true },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge)
                            },
                            onClick = { menuOpen = false; onAbout() },
                        )
                    }
                }
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

    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = stringResource(R.string.empty_trash),
            message = stringResource(R.string.empty_trash_confirm) + ".",
            confirmText = stringResource(R.string.delete),
            onDismiss = { confirmEmptyTrash = false },
            onConfirm = {
                confirmEmptyTrash = false
                vm.emptyTrash()
            },
        )
    }
}

/** A screen filling the window, with a back button and title above whatever [content] puts in its Column — used
 * for Trash and Settings so they navigate like the rest of the app instead of popping up as a dialog.
 * [actions] sits at the title row's trailing end, for a screen-specific button such as Trash's "clear". */
@Composable
private fun AppScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            actions()
        }
        content()
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
private val NAV_BUTTON = 80.dp
private val NAV_ICON = 36.dp

// Black icon glyphs on the trash button instead of white: a black icon on a
// saturated fill reads more clearly than white does at this size.
private val ButtonIconColor = Color.Black

@Composable
private fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    busy: Boolean,
    onTrash: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    // The inverse-surface pair, not a fixed colour: it is dark in a light
    // theme and light in a dark one, always the opposite of the window
    // behind it, so back/forward stay visible whichever theme is in force.
    val navContainer = MaterialTheme.colorScheme.inverseSurface
    val navContent = MaterialTheme.colorScheme.inverseOnSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onTrash,
            enabled = !busy,
            modifier = Modifier.size(BOTTOM_BUTTON),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = ButtonIconColor,
            ),
        ) { Icon(Icons.Filled.Delete, stringResource(R.string.to_trash)) }

        FilledIconButton(
            onClick = onBack,
            enabled = canGoBack && !busy,
            modifier = Modifier.size(NAV_BUTTON),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = navContainer,
                contentColor = navContent,
                disabledContainerColor = navContainer.copy(alpha = 0.35f),
                disabledContentColor = navContent.copy(alpha = 0.4f),
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.previous_photo),
                modifier = Modifier.size(NAV_ICON),
            )
        }

        FilledIconButton(
            onClick = onForward,
            enabled = canGoForward && !busy,
            modifier = Modifier.size(NAV_BUTTON),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = navContainer,
                contentColor = navContent,
                disabledContainerColor = navContainer.copy(alpha = 0.35f),
                disabledContentColor = navContent.copy(alpha = 0.4f),
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                stringResource(R.string.next_photo),
                modifier = Modifier.size(NAV_ICON),
            )
        }
    }
}
