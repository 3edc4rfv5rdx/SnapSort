package xx.snapsort.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.updater.Updater
import xx.snapsort.AppSettings
import xx.snapsort.MAX_SORT_FOLDERS
import xx.snapsort.QueueOrder
import xx.snapsort.R
import xx.snapsort.SORT_DIR_PREFIX
import xx.snapsort.SortFolder
import xx.snapsort.SortIcon
import xx.snapsort.ThemeMode
import xx.snapsort.cleanSortName
import xx.snapsort.currentLanguageTag
import xx.snapsort.setLanguageTag
import xx.snapsort.supportedLanguages

/** Which editor is open; only one can be at a time. */
private enum class Editing { NONE, THEME, ACCENT, LANGUAGE, ORDER, SORT_FOLDERS }

/**
 * Theme, accent colour, language, the start-up update check, the queue's
 * order, and whether browse position is remembered.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val themeMode by AppSettings.themeMode.collectAsState()
    val accentIndex by AppSettings.accentIndex.collectAsState()
    val rememberPosition by AppSettings.rememberPosition.collectAsState()
    val queueOrder by AppSettings.queueOrder.collectAsState()
    val sortFolders by AppSettings.sortFolders.collectAsState()

    val systemLabel = stringResource(R.string.language_system)
    val languages = remember(context, systemLabel) { supportedLanguages(context, systemLabel) }
    val languageTag = remember(context) { currentLanguageTag(context) }
    val languageLabel = languages.firstOrNull { it.tag == languageTag }?.label ?: systemLabel

    // Saveable: a new theme or language recreates the activity under an open dialog.
    var editing by rememberSaveable { mutableStateOf(Editing.NONE) }
    // The updater keeps this flag in its own preferences file.
    var updateCheck by remember { mutableStateOf(Updater.isEnabled(context)) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingRow(
            label = stringResource(R.string.setting_theme),
            value = stringResource(themeMode.labelRes()),
            onClick = { editing = Editing.THEME },
        )
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editing = Editing.ACCENT }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setting_accent),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            AccentSwatch(accentAt(accentIndex), selected = false, onClick = { editing = Editing.ACCENT })
        }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_language),
            value = languageLabel,
            onClick = { editing = Editing.LANGUAGE },
        )
        HorizontalDivider()
        // The whole row is not clickable: the switch is the control.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setting_update_check),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = updateCheck,
                onCheckedChange = {
                    updateCheck = it
                    Updater.setEnabled(context, it)
                },
            )
        }
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.sort_folders),
            value = formatCount(sortFolders.size),
            onClick = { editing = Editing.SORT_FOLDERS },
        )
        HorizontalDivider()
        SettingRow(
            label = stringResource(R.string.setting_order),
            value = stringResource(queueOrder.labelRes()),
            onClick = { editing = Editing.ORDER },
        )
        HorizontalDivider()
        // The whole row is not clickable: the switch is the control.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.setting_remember_position),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = rememberPosition,
                onCheckedChange = { AppSettings.setRememberPosition(context, it) },
            )
        }
    }

    when (editing) {
        Editing.THEME -> ChoiceDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setThemeMode(context, it)
                editing = Editing.NONE
            },
        )

        Editing.ACCENT -> AlertDialog(
            onDismissRequest = { editing = Editing.NONE },
            title = { Text(stringResource(R.string.setting_accent)) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AccentPalette.forEachIndexed { index, color ->
                        AccentSwatch(color, selected = index == accentIndex, onClick = {
                            AppSettings.setAccentIndex(context, index)
                            editing = Editing.NONE
                        })
                    }
                }
            },
            confirmButton = { DialogDismissButton(stringResource(R.string.cancel)) { editing = Editing.NONE } },
        )

        Editing.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.setting_language),
            options = languages,
            selected = languages.firstOrNull { it.tag == languageTag },
            label = { it.label },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                editing = Editing.NONE
                // Persisted, and the activity recreated in the new language.
                activity?.let { a -> setLanguageTag(a, it.tag) }
            },
        )

        Editing.ORDER -> ChoiceDialog(
            title = stringResource(R.string.setting_order),
            options = QueueOrder.entries,
            selected = queueOrder,
            label = { stringResource(it.labelRes()) },
            onDismiss = { editing = Editing.NONE },
            onPick = {
                AppSettings.setQueueOrder(context, it)
                editing = Editing.NONE
            },
        )

        Editing.SORT_FOLDERS -> SortFoldersDialog(
            folders = sortFolders,
            onDismiss = { editing = Editing.NONE },
            onSave = {
                AppSettings.setSortFolders(context, it)
                editing = Editing.NONE
            },
        )

        Editing.NONE -> Unit
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The folders the buttons over the photo sort into: up to [MAX_SORT_FOLDERS]
 * rows of an icon and a name, each a subfolder made beside the photo itself.
 * Nothing is kept until OK, so a half-typed name can be left behind.
 */
@Composable
private fun SortFoldersDialog(folders: List<SortFolder>, onDismiss: () -> Unit, onSave: (List<SortFolder>) -> Unit) {
    val editing = remember { folders.map { it.icon to it.name }.toMutableStateList() }
    var iconFor by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_folders)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                editing.forEachIndexed { index, (icon, name) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { iconFor = index }) {
                            Icon(icon.vector(), stringResource(R.string.sort_folder_icon))
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { editing[index] = icon to it },
                            singleLine = true,
                            prefix = { Text(SORT_DIR_PREFIX) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { editing.removeAt(index) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.sort_folder_remove))
                        }
                    }
                }
                if (editing.size < MAX_SORT_FOLDERS) {
                    DialogDismissButton(stringResource(R.string.sort_folder_add)) {
                        editing.add(SortIcon.STAR to "")
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(stringResource(R.string.ok), danger = false) {
                // Cleaned here, not while typing: a name being edited would
                // otherwise lose the space or the dash as it is typed.
                onSave(
                    editing.map { (icon, name) -> SortFolder(cleanSortName(name), icon) }
                        .filter { it.name.isNotEmpty() }
                        .distinctBy { it.name },
                )
            }
        },
        dismissButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
    iconFor?.let { index ->
        IconPickerDialog(
            onDismiss = { iconFor = null },
            onPick = { picked ->
                editing[index] = picked to editing[index].second
                iconFor = null
            },
        )
    }
}

private const val ICONS_PER_ROW = 6

/** The icons a sort folder's button can carry, to pick one from. */
@Composable
private fun IconPickerDialog(onDismiss: () -> Unit, onPick: (SortIcon) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_folder_icon)) },
        text = {
            Column {
                SortIcon.entries.chunked(ICONS_PER_ROW).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { icon ->
                            IconButton(onClick = { onPick(icon) }) { Icon(icon.vector(), null) }
                        }
                    }
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.cancel), onDismiss) },
    )
}

private fun QueueOrder.labelRes(): Int = when (this) {
    QueueOrder.NAME -> R.string.order_name
    QueueOrder.DATE_OLDEST -> R.string.order_oldest
    QueueOrder.DATE_NEWEST -> R.string.order_newest
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
