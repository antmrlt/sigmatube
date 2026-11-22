package app.sigmatube.ui.playlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sigmatube.data.LocalPlaylist
import app.sigmatube.data.LocalPlaylists
import app.sigmatube.data.VideoItem
import app.sigmatube.ui.components.EditableVideoRow
import app.sigmatube.ui.components.MessageState
import app.sigmatube.ui.components.Motion
import app.sigmatube.ui.components.Pill
import app.sigmatube.ui.components.PlaylistNameDialog
import app.sigmatube.ui.components.bouncyClickable
import app.sigmatube.ui.components.rememberReorderState
import coil3.compose.AsyncImage

/** One of the user's own playlists: play it, reorder it (drag), remove videos, rename or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    id: String,
    playlists: LocalPlaylists,
    onBack: () -> Unit,
    onPlay: (videos: List<VideoItem>, start: Int, shuffle: Boolean) -> Unit,
) {
    val all by playlists.playlists.collectAsStateWithLifecycle()
    val playlist = all.firstOrNull { it.id == id }
    LaunchedEffect(playlist == null) { if (playlist == null) onBack() }
    if (playlist == null) return

    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Renommer") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer la playlist") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        val videos = playlist.videos
        val reorder = rememberReorderState(
            listState,
            canMoveTo = { (it as? String)?.startsWith("v:") == true },
            onMove = { from, to ->
                val i = videos.indexOfFirst { "v:${it.url}" == from }
                val j = videos.indexOfFirst { "v:${it.url}" == to }
                if (i >= 0 && j >= 0) playlists.move(id, i, j)
            },
        )
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PlaylistCover(playlist, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        countLabel(videos.size) + " · Sur cet appareil",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (videos.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(filled = true, onClick = { onPlay(videos, 0, false) }) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                Text("Tout lire", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                            }
                            Pill(onClick = { onPlay(videos, 0, true) }) {
                                Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp))
                                Text("Aléatoire", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (videos.isEmpty()) {
                item(key = "empty") {
                    MessageState(
                        "Cette playlist est vide",
                        "Ajoute des vidéos avec « Enregistrer dans une playlist » dans le menu ⋮ d'une vidéo.",
                        Modifier.fillMaxWidth().height(320.dp),
                        icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    )
                }
            }
            items(videos, key = { "v:${it.url}" }) { video ->
                val key = "v:${video.url}"
                EditableVideoRow(
                    video = video,
                    reorder = reorder,
                    key = key,
                    onClick = { onPlay(videos, videos.indexOf(video), false) },
                    onRemove = { playlists.remove(id, video.url) },
                    modifier = if (reorder.draggingKey == key) Modifier else Modifier.animateItem(
                        fadeInSpec = tween(Motion.FADE_MS),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(120),
                    ),
                )
            }
        }
    }

    if (renaming) {
        PlaylistNameDialog("Renommer la playlist", "Renommer", initial = playlist.name, onDismiss = { renaming = false }) {
            playlists.rename(id, it)
            renaming = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${playlist.name} » ?") },
            text = { Text("La playlist sera supprimée de cet appareil. Les vidéos ne sont pas touchées.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; playlists.delete(id) }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

fun countLabel(n: Int) = if (n == 0) "Aucune vidéo" else "$n vidéo${if (n > 1) "s" else ""}"

/** 16:9 cover (first video's thumbnail), with the count over it. */
@Composable
fun PlaylistCover(playlist: LocalPlaylist, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (playlist.thumbnail != null) {
            AsyncImage(playlist.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, null, Modifier.size(14.dp), tint = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.width(4.dp))
            Text("${playlist.videos.size}", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

/** Cover + name + count, for the horizontal rows of playlists (Musique, Vous). */
@Composable
fun LocalPlaylistTile(playlist: LocalPlaylist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(168.dp).bouncyClickable(pressedScale = 0.95f, onClick = onClick)) {
        PlaylistCover(playlist, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            countLabel(playlist.videos.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
