package app.sigmatube.ui.watch

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sigmatube.data.VideoItem
import app.sigmatube.ui.components.EditableVideoRow
import app.sigmatube.ui.components.MessageState
import app.sigmatube.ui.components.Motion
import app.sigmatube.ui.components.Pill
import app.sigmatube.ui.components.rememberReorderState

/** Card under the video's actions: how many videos are queued and which one comes next. */
@Composable
fun QueueTeaser(queue: List<VideoItem>, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.QueueMusic, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "File d'attente · ${queue.size} vidéo${if (queue.size > 1) "s" else ""}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "À suivre : ${queue.first().title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The queue panel: tap to play, drag to reorder, × to remove. */
@Composable
fun QueueContent(
    queue: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    onRemove: (VideoItem) -> Unit,
    onMove: (Int, Int) -> Unit,
    onShuffle: () -> Unit,
    onClear: () -> Unit,
) {
    if (queue.isEmpty()) {
        MessageState(
            "La file d'attente est vide",
            "Utilise « Lire ensuite » ou « Lire en dernier » dans le menu ⋮ d'une vidéo.",
            Modifier.fillMaxSize(),
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
        )
        return
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState,
        canMoveTo = { (it as? String)?.startsWith("q:") == true },
        onMove = { from, to ->
            val i = queue.indexOfFirst { "q:${it.url}" == from }
            val j = queue.indexOfFirst { "q:${it.url}" == to }
            if (i >= 0 && j >= 0) onMove(i, j)
        },
    )
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "actions") {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(onClick = onShuffle) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Text("Mélanger", style = MaterialTheme.typography.labelLarge)
                }
                Pill(onClick = onClear) {
                    Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
                    Text("Tout effacer", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        items(queue, key = { "q:${it.url}" }) { video ->
            val key = "q:${video.url}"
            EditableVideoRow(
                video = video,
                reorder = reorder,
                key = key,
                onClick = { onPlay(video) },
                onRemove = { onRemove(video) },
                modifier = if (reorder.draggingKey == key) Modifier else Modifier.animateItem(
                    fadeInSpec = tween(Motion.FADE_MS),
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    fadeOutSpec = tween(120),
                ),
            )
        }
        item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
    }
}
