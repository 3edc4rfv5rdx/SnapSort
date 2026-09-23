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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xx.snapsort.AppSettings
import xx.snapsort.R
import xx.snapsort.SnapSortViewModel
import xx.snapsort.currentYear
import xx.snapsort.YearSortPhase
import xx.snapsort.pathOnVolume
import xx.snapsort.rememberDeviceRotation

/**
 * The whole app: one photo at a time, kept or trashed with a button tap, with
 * a folder to pick before any of that and a ⋮ menu for the trash, settings
 * and about.
 */
@Composable
fun SwipeScreen(
    vm: SnapSortViewModel,
    onGrantAccess: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    // Saveable: picking a language recreates the activity, and a plain
    // remember would drop the user back on the photo screen mid-Settings.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var confirmRestoreAll by remember { mutableStateOf(false) }
    val sortFolders by AppSettings.sortFolders.collectAsState()

    Box(modifier.fillMaxSize()) {
        when {
            vm.trashOpen -> AppScreen(
                title = stringResource(R.string.trash),
                onBack = vm::closeTrash,
                actions = {
                    // An icon, not a labelled button like "Clear": both
                    // labels side by side leave the title no room on a
                    // phone-width screen in Russian or Ukrainian.
                    IconButton(
                        onClick = { confirmRestoreAll = true },
                        enabled = !vm.busy && !vm.trashEntries.isNullOrEmpty(),
                        modifier = Modifier.size(LARGE_BUTTON),
                    ) {
                        Icon(
                            Icons.Filled.RestoreFromTrash,
                            stringResource(R.string.restore_all),
                            Modifier.size(LARGE_ICON),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
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
                    job = vm.trashJob,
                    jobDone = vm.trashJobDone,
                    jobTotal = vm.trashJobTotal,
                    volumeRoot = vm.volumeRoot,
                    onRestore = vm::restoreFromTrash,
                    onPurge = vm::purgeFromTrash,
                    modifier = Modifier.weight(1f),
                )
            }

            settingsOpen -> AppScreen(title = stringResource(R.string.settings), onBack = { settingsOpen = false }) {
                SettingsScreen(modifier = Modifier.weight(1f))
            }

            pickerOpen -> AppScreen(
                title = stringResource(R.string.pick_folder),
                onBack = { pickerOpen = false },
            ) {
                FolderPickerScreen(
                    onPick = { dir ->
                        pickerOpen = false
                        vm.openFolder(dir)
                    },
                    modifier = Modifier.weight(1f),
                )
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
                        icon = null,
                        title = stringResource(R.string.pick_folder),
                        message = stringResource(R.string.pick_folder_hint),
                        action = { PickFolderButton { pickerOpen = true } },
                    )

                    vm.yearSortPhase != null -> {
                        // Back stops it between two files: nothing is left half moved.
                        BackHandler(onBack = vm::cancelYearSort)
                        val reading = vm.yearSortPhase == YearSortPhase.READING
                        ScanningState(
                            text = stringResource(
                                if (reading) R.string.reading_dates else R.string.years_moving,
                                formatCount(vm.yearSortDone),
                                formatCount(vm.yearSortTotal),
                            ),
                        )
                    }

                    vm.scanning -> ScanningState(
                        text = if (vm.readingDates) {
                            stringResource(
                                R.string.reading_dates,
                                formatCount(vm.datesRead),
                                formatCount(vm.datesTotal),
                            )
                        } else {
                            labelValue(stringResource(R.string.scanning), formatCount(vm.scannedCount))
                        },
                    )

                    vm.images.isEmpty() -> EmptyState(
                        icon = null,
                        title = stringResource(R.string.no_photos),
                        message = null,
                        action = { PickFolderButton { pickerOpen = true } },
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
                                // Down the right edge, clear of the count pill
                                // and the menu buttons above: one tap puts the
                                // photo into that folder beside it.
                                Column(
                                    Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(SORT_BUTTON_GAP),
                                ) {
                                    for (folder in sortFolders) {
                                        // Already in that folder: nothing to move it into.
                                        if (entry.file.parentFile?.name == folder.dirName) continue
                                        OverlayIconButton(
                                            icon = folder.icon.vector(),
                                            contentDescription = stringResource(R.string.move_to, folder.name),
                                            onClick = { vm.moveInto(folder) },
                                            enabled = !vm.busy,
                                        )
                                    }
                                }
                                CountPill(
                                    current = vm.index + 1,
                                    total = vm.images.size,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 12.dp, top = 12.dp, end = TOP_BUTTONS_WIDTH + 12.dp),
                                )
                            }
                            BottomBar(
                                rotation = rotation,
                                canGoBack = vm.index > 0,
                                canGoForward = vm.index < vm.images.lastIndex,
                                busy = vm.busy,
                                onTrash = vm::trash,
                                onBack = vm::previous,
                                onForward = vm::next,
                            )
                            FilePill(
                                name = entry.file.name,
                                path = entry.file.parentFile?.let { pathOnVolume(it, vm.volumeRoot) }.orEmpty(),
                                filePath = entry.file.path,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                            )
                        }
                    }
                }

                // The menu's most used entries as buttons of their own; the
                // menu keeps them too. Not turned with the phone: this is a
                // portrait screen, and only the photo and its controls follow.
                Row(
                    Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.systemBars).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(TOP_BUTTON_GAP),
                ) {
                    OverlayIconButton(
                        icon = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.trash),
                        onClick = vm::openTrash,
                        enabled = vm.hasFolder,
                    )
                    OverlayIconButton(
                        icon = Icons.Filled.FolderOpen,
                        contentDescription = stringResource(R.string.change_folder),
                        onClick = { pickerOpen = true },
                    )
                    OverlayIconButton(
                        icon = Icons.Filled.Storage,
                        contentDescription = stringResource(R.string.disk_space),
                        onClick = vm::openDiskSpace,
                    )
                    Box {
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
                                onClick = { menuOpen = false; pickerOpen = true },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.trash), style = MaterialTheme.typography.titleLarge)
                                },
                                // The trash lives under the picked folder: without
                                // one there is nothing to list, and opening it
                                // would leave the screen loading forever.
                                enabled = vm.hasFolder,
                                onClick = { menuOpen = false; vm.openTrash() },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.disk_space),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                },
                                onClick = { menuOpen = false; vm.openDiskSpace() },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.sort_by_year),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                },
                                enabled = vm.hasFolder && !vm.busy && !vm.scanning,
                                onClick = { menuOpen = false; vm.planYearSort() },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.reset_position),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                },
                                onClick = { menuOpen = false; vm.resetPosition() },
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
            onDismiss = { confirmEmptyTrash = false },
            onConfirm = {
                confirmEmptyTrash = false
                vm.emptyTrash()
            },
        )
    }

    if (confirmRestoreAll) {
        ConfirmDialog(
            title = stringResource(R.string.restore_all),
            message = stringResource(R.string.restore_all_confirm) + ".\n" +
                labelValue(stringResource(R.string.trash_photo_count), formatCount(vm.trashEntries?.size ?: 0)),
            danger = false,
            onDismiss = { confirmRestoreAll = false },
            onConfirm = {
                confirmRestoreAll = false
                vm.restoreAllFromTrash()
            },
        )
    }

    vm.yearPlan?.let { plan ->
        val lines = buildList {
            add(stringResource(R.string.years_confirm) + ".")
            plan.filesByYear.forEach { (year, count) -> add(labelValue(year.toString(), formatCount(count))) }
            if (plan.thisYearFiles > 0) {
                val thisYear = stringResource(R.string.years_this_year, currentYear().toString())
                add(labelValue(thisYear, formatCount(plan.thisYearFiles)))
            }
            if (plan.undatedFiles > 0) {
                add(labelValue(stringResource(R.string.years_undated), formatCount(plan.undatedFiles)))
            }
        }
        ConfirmDialog(
            title = stringResource(R.string.sort_by_year),
            message = lines.joinToString("\n"),
            danger = false,
            onDismiss = vm::dismissYearPlan,
            onConfirm = vm::sortIntoYears,
        )
    }

    if (vm.diskSpaceOpen) {
        DiskSpaceDialog(
            freeBytes = vm.diskFreeBytes,
            totalBytes = vm.diskTotalBytes,
            trashCount = vm.trashCount,
            trashBytes = vm.trashBytes,
            sessionCount = vm.sessionTrashedCount,
            sessionBytes = vm.sessionTrashedBytes,
            onDismiss = vm::closeDiskSpace,
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

/**
 * A screen with nothing to show yet: [icon] above the [title], [action] below.
 * Without an icon the action takes its place on top — a folder button under a
 * folder picture only said the same thing twice.
 */
@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    message: String?,
    action: (@Composable () -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
        } else if (action != null) {
            action()
            Spacer(Modifier.height(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (icon != null && action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
private fun ScanningState(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text)
    }
}

// The buttons at the top right — trash, folder, disk space, ⋮ — at the 48dp
// an IconButton takes, their gaps and the row's own 4dp: how far the count
// pill at the top left has to stay clear of them.
// Well apart, unlike the row at the top: these sit under a thumb that moves
// down the edge between them, and a mis-tap puts a photo in the wrong folder.
private val SORT_BUTTON_GAP = 20.dp

private val TOP_BUTTON_GAP = 4.dp
private val TOP_BUTTONS_WIDTH = 4.dp + 48.dp * 4 + TOP_BUTTON_GAP * 3

private val BOTTOM_BUTTON = 64.dp
private val NAV_BUTTON = 80.dp
private val NAV_ICON = 36.dp

// Black icon glyphs on the trash button instead of white: a black icon on a
// saturated fill reads more clearly than white does at this size.
private val ButtonIconColor = Color.Black

@Composable
private fun BottomBar(
    rotation: Int,
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
    // The glyphs turn with the photo, so they read upright however the phone
    // is held, and the arrows point the way a swipe on that photo goes. The
    // buttons themselves stay put: the layout is portrait whatever happens.
    val glyph = Modifier.rotate(rotation.toFloat())
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
        ) { Icon(Icons.Filled.Delete, stringResource(R.string.to_trash), modifier = glyph) }

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
                modifier = glyph.size(NAV_ICON),
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
                modifier = glyph.size(NAV_ICON),
            )
        }
    }
}
