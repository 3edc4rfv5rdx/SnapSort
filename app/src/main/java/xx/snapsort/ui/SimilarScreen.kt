package xx.snapsort.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xx.snapsort.ImageEntry
import xx.snapsort.R
import xx.snapsort.SimilarPhase
import xx.snapsort.rememberDeviceRotation

/**
 * One group of similar shots at a time: a tap on a shot shows it whole, a tap
 * on its thumbs-up keeps it, and then the rest go to the trash — or the group is
 * skipped. While [phase] is set the search is still running, with progress.
 */
@Composable
fun SimilarScreen(
    phase: SimilarPhase?,
    done: Int,
    total: Int,
    group: List<ImageEntry>?,
    groupIndex: Int,
    busy: Boolean,
    onSkip: () -> Unit,
    onTrashRest: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (phase != null || group == null) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(
                    if (phase == SimilarPhase.COMPARING) R.string.similar_comparing else R.string.reading_dates,
                    formatCount(done),
                    formatCount(total),
                ),
            )
        }
        return
    }

    // Keyed on the group: the next one starts with nothing kept, on the grid.
    var kept by rememberSaveable(groupIndex) { mutableStateOf(listOf<String>()) }
    var viewing by rememberSaveable(groupIndex) { mutableIntStateOf(-1) }
    var confirmAll by rememberSaveable(groupIndex) { mutableStateOf(false) }
    val toggle = { path: String -> kept = if (path in kept) kept - path else kept + path }

    val shown = group.getOrNull(viewing)
    if (shown != null) {
        BackHandler { viewing = -1 }
        val rotation = rememberDeviceRotation()
        // One zoom for the whole group, so shots are compared close in on the
        // same spot; back on the grid it is dropped, and the next look starts afresh.
        val zoom = remember { PhotoZoom() }
        val forward = { if (viewing < group.lastIndex) viewing++ }
        val backward = { if (viewing > 0) viewing-- }
        Box(modifier.fillMaxSize()) {
            // A swipe goes through the group's shots, so two can be compared back and forth.
            PhotoView(
                path = shown.file.path,
                rotation = rotation,
                onSwipeForward = forward,
                onSwipeBackward = backward,
                zoom = zoom,
                modifier = Modifier.fillMaxSize(),
            )
            // Buttons as well as the swipe: while zoomed in, a drag moves the photo instead.
            // Their arrows turn with the photo, as on the main screen.
            Row(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.previous_photo),
                    enabled = viewing > 0,
                    rotation = rotation,
                    onClick = backward,
                )
                StepButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.next_photo),
                    enabled = viewing < group.lastIndex,
                    rotation = rotation,
                    onClick = forward,
                )
            }
            InverseIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = { viewing = -1 },
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            CountPill(
                current = viewing + 1,
                total = group.size,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            )
            KeepToggle(
                kept = shown.file.path in kept,
                onToggle = { toggle(shown.file.path) },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }
        return
    }

    val out = group.count { it.file.path !in kept }
    Column(modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.similar_hint),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(group, key = { _, entry -> entry.file.path }) { i, entry ->
                val isKept = entry.file.path in kept
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(shape)
                        .clickable { viewing = i },
                ) {
                    PhotoThumbnail(path = entry.file.path, modifier = Modifier.fillMaxSize())
                    // A kept shot is framed in the colour its mark turns.
                    if (isKept) Box(Modifier.fillMaxSize().border(4.dp, KeptFill, shape))
                    KeepToggle(
                        kept = isKept,
                        onToggle = { toggle(entry.file.path) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(onClick = onSkip, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.similar_skip), maxLines = 1)
            }
            // With nothing kept the whole group goes, so that asks first:
            // it may as well be a shot forgotten to be kept.
            Button(
                onClick = { if (kept.isEmpty()) confirmAll = true else onTrashRest(kept.toSet()) },
                enabled = !busy && out > 0,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.similar_trash_rest, formatCount(out)), maxLines = 1)
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = stringResource(R.string.to_trash),
            message = stringResource(R.string.similar_trash_all_confirm) + ".\n" +
                labelValue(stringResource(R.string.trash_photo_count), formatCount(group.size)),
            onDismiss = { confirmAll = false },
            onConfirm = {
                confirmAll = false
                onTrashRest(emptySet())
            },
        )
    }
}

private val STEP_BUTTON = 56.dp
private val STEP_ICON = 28.dp

/** Back or forward through the group's shots: the main screen's arrows in the inverse pair, a size smaller. */
@Composable
private fun StepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    rotation: Int,
    onClick: () -> Unit,
) {
    val container = MaterialTheme.colorScheme.inverseSurface
    val content = MaterialTheme.colorScheme.inverseOnSurface
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(STEP_BUTTON),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.35f),
            disabledContentColor = content.copy(alpha = 0.4f),
        ),
    ) {
        Icon(icon, contentDescription, Modifier.rotate(rotation.toFloat()).size(STEP_ICON))
    }
}

private val KEEP_MARK = 34.dp
private val KEEP_GLYPH = 20.dp

// Fixed, not the theme's inverse pair: over a photo the unticked mark is
// already dark in either theme, and in a light one the inverse pair is dark
// too, so a ticked mark would look the same as an unticked one.
private val KeptFill = Color.White
private val KeptInk = Color.Black

/**
 * The thumbs-up that keeps a shot: white on dark over the photo, turned the
 * other way round — dark on white — once ticked. Small to look at, a full
 * [LARGE_BUTTON] to hit.
 */
@Composable
private fun KeepToggle(kept: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.similar_keep)
    val fill = if (kept) KeptFill else Color.Black.copy(alpha = 0.55f)
    val ink = if (kept) KeptInk else Color.White
    Box(
        modifier
            .size(LARGE_BUTTON)
            .toggleable(value = kept, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(KEEP_MARK)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = ink, modifier = Modifier.size(KEEP_GLYPH))
        }
    }
}
