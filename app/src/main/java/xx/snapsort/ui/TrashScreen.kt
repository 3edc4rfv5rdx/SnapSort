package xx.snapsort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.snapsort.R
import xx.snapsort.Trash
import xx.snapsort.TrashJob
import xx.snapsort.pathOnVolume
import xx.snapsort.rememberDeviceRotation
import java.io.File
import java.text.DateFormat
import java.util.Date

/** What the trash holds: restore/delete-for-good per item, a tap-to-view of the photo, and the total count. */
@Composable
fun TrashScreen(
    entries: List<Trash.Entry>?,
    busy: Boolean,
    job: TrashJob?,
    jobDone: Int,
    jobTotal: Int,
    volumeRoot: File?,
    onRestore: (Trash.Entry) -> Unit,
    onPurge: (Trash.Entry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // By id: an entry object does not survive a reload or the activity being recreated.
    var purgeId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingId by rememberSaveable { mutableStateOf<String?>(null) }
    // The last photo opened full-screen, marked in the list on the way back so
    // it is plain which one was looked at. The list's scroll lives up here
    // too: the viewer replaces the list, and a state kept inside it would be
    // gone by then, dropping the user back at the top.
    var viewedId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    if (job != null) {
        TrashJobState(job, jobDone, jobTotal, modifier)
        return
    }
    if (entries == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.trash_empty_state), color = MaterialTheme.colorScheme.onSurface)
        }
        return
    }

    val viewing = entries.firstOrNull { it.id == viewingId }
    if (viewing != null) {
        BackHandler { viewingId = null }
        Box(modifier.fillMaxSize()) {
            PhotoView(
                path = viewing.item.path,
                rotation = rememberDeviceRotation(),
                modifier = Modifier.fillMaxSize(),
            )
            InverseIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = { viewingId = null },
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        return
    }

    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Column(modifier.fillMaxSize()) {
        Text(
            text = dotted(
                labelValue(stringResource(R.string.trash_photo_count), formatCount(entries.size)),
                formatSize(context, entries.sumOf { it.size }),
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(entries, key = { it.id }) { entry ->
                // The last-viewed row in the theme's inverse pair, the same
                // one the nav buttons use: dark on a light theme, light on a dark one.
                val viewed = entry.id == viewedId
                val rowColor = if (viewed) MaterialTheme.colorScheme.inverseSurface else Color.Transparent
                val textColor = if (viewed) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
                CompositionLocalProvider(LocalContentColor provides textColor) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(rowColor)
                            .clickable {
                                viewingId = entry.id
                                viewedId = entry.id
                            }
                            .padding(start = 0.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PhotoThumbnail(path = entry.item.path, modifier = Modifier.size(64.dp))
                        Column(
                            Modifier.weight(1f).padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = pathOnVolume(entry.originalParent, volumeRoot),
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = dotted(dateFormat.format(Date(entry.deletedAt)), formatSize(context, entry.size)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                            )
                        }
                        EntryMenu(
                            enabled = !busy,
                            onRestore = { onRestore(entry) },
                            onPurge = { purgeId = entry.id },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    entries.firstOrNull { it.id == purgeId }?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.delete_forever),
            message = dotted(entry.name, formatSize(context, entry.size)),
            onDismiss = { purgeId = null },
            onConfirm = {
                purgeId = null
                onPurge(entry)
            },
        )
    }
}

/** Clearing or restoring the whole trash, in place of the list whose files are going away. */
@Composable
private fun TrashJobState(job: TrashJob, done: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LinearProgressIndicator(
            progress = { if (total > 0) done.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (job == TrashJob.RESTORE) R.string.trash_restoring else R.string.trash_emptying,
                formatCount(done),
                formatCount(total),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The actions on one trash entry, behind its ⋮ button. */
@Composable
private fun EntryMenu(enabled: Boolean, onRestore: () -> Unit, onPurge: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MoreButton(onClick = { open = true }, enabled = enabled)
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.restore), style = MaterialTheme.typography.titleLarge) },
                onClick = {
                    open = false
                    onRestore()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.delete_forever),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    open = false
                    onPurge()
                },
            )
        }
    }
}
