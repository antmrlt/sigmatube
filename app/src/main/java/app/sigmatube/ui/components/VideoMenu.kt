package app.sigmatube.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sigmatube.data.LocalPlaylists
import app.sigmatube.data.VideoItem
import app.sigmatube.ui.theme.AppIcons
import kotlinx.coroutines.launch

/** What the ⋮ menu of a video can do; implemented once at the root of the UI. */
class VideoMenuActions(
    val playNext: (VideoItem) -> Unit,
    val playLast: (VideoItem) -> Unit,
    val saveToWatchLater: (VideoItem) -> Unit,
    val download: (VideoItem, audioOnly: Boolean) -> Unit,
    val notInterested: (VideoItem) -> Unit,
    val dontRecommendChannel: (VideoItem) -> Unit,
)

/** An entry shown above the usual ones, for the screen that opened the menu (e.g. the music player). */
class MenuExtra(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Which video's menu (or follow-up sheet) is open. One instance for the whole app. */
@Stable
class VideoMenuState {
    internal var options by mutableStateOf<VideoItem?>(null)
    internal var extras by mutableStateOf<List<MenuExtra>>(emptyList())
    internal var saveTo by mutableStateOf<VideoItem?>(null)
    internal var download by mutableStateOf<VideoItem?>(null)

    fun open(video: VideoItem, extras: List<MenuExtra> = emptyList()) {
        this.extras = extras
        options = video
    }

    fun saveToPlaylist(video: VideoItem) {
        saveTo = video
    }
}

val LocalVideoMenu = staticCompositionLocalOf<VideoMenuState?> { null }

/** Renders the video options sheet and its follow-ups ("Enregistrer dans…", "Télécharger"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMenuHost(state: VideoMenuState, actions: VideoMenuActions, playlists: LocalPlaylists) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    state.options?.let { video ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        fun pick(action: () -> Unit) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                state.options = null
                action()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { state.options = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (state.extras.isNotEmpty()) {
                    state.extras.forEach { extra -> MenuEntry(extra.icon, extra.label) { pick(extra.onClick) } }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                MenuEntry(Icons.AutoMirrored.Outlined.PlaylistPlay, "Lire ensuite dans la file d'attente") { pick { actions.playNext(video) } }
                MenuEntry(Icons.AutoMirrored.Outlined.QueueMusic, "Lire en dernier dans la file d'attente") { pick { actions.playLast(video) } }
                MenuEntry(Icons.Outlined.WatchLater, "Enregistrer dans « À regarder plus tard »") { pick { actions.saveToWatchLater(video) } }
                MenuEntry(Icons.AutoMirrored.Outlined.PlaylistAdd, "Enregistrer dans une playlist") { pick { state.saveTo = video } }
                MenuEntry(Icons.Outlined.Download, "Télécharger") { pick { state.download = video } }
                MenuEntry(AppIcons.Share, "Partager") { pick { shareVideo(context, video.title, video.url) } }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MenuEntry(Icons.Outlined.NotInterested, "Pas intéressé") { pick { actions.notInterested(video) } }
                if (video.channelUrl != null) {
                    MenuEntry(Icons.Outlined.Block, "Ne pas recommander la chaîne") { pick { actions.dontRecommendChannel(video) } }
                }
            }
        }
    }

    state.saveTo?.let { video -> SaveToPlaylistSheet(video, playlists, onDismiss = { state.saveTo = null }) }

    state.download?.let { video ->
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        fun start(audioOnly: Boolean) {
            state.download = null
            // Progress shows in a notification; ask once on Android 13+ (the download runs either way).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            actions.download(video, audioOnly)
        }
        AlertDialog(
            onDismissRequest = { state.download = null },
            title = { Text("Télécharger") },
            text = {
                Column {
                    Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.size(12.dp))
                    MenuEntry(Icons.Outlined.Movie, "Vidéo (MP4)", padding = 4.dp) { start(audioOnly = false) }
                    MenuEntry(Icons.Outlined.Audiotrack, "Audio uniquement (M4A)", padding = 4.dp) { start(audioOnly = true) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { state.download = null }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveToPlaylistSheet(video: VideoItem, playlists: LocalPlaylists, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all by playlists.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Enregistrer dans…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (all.isEmpty()) {
                Text(
                    "Aucune playlist pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(all, key = { it.id }) { playlist ->
                    val checked = playlist.videos.any { it.url == video.url }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) playlists.remove(playlist.id, video.url) else playlists.add(playlist.id, video)
                                Toast.makeText(
                                    context,
                                    if (checked) "Supprimée de « ${playlist.name} »" else "Ajoutée à « ${playlist.name} »",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .padding(start = 8.dp, end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${playlist.videos.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MenuEntry(Icons.Rounded.Add, "Nouvelle playlist") { creating = true }
        }
    }
    if (creating) {
        PlaylistNameDialog(
            title = "Nouvelle playlist",
            confirmLabel = "Créer",
            onDismiss = { creating = false },
        ) { name ->
            creating = false
            playlists.create(name, video)
            Toast.makeText(context, "Ajoutée à « ${name.trim().ifEmpty { "Nouvelle playlist" }} »", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
}

/** Name prompt used to create or rename a playlist. */
@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("Nom de la playlist") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onConfirm(value.text) }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun MenuEntry(icon: ImageVector, label: String, padding: androidx.compose.ui.unit.Dp = 20.dp, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = padding, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
