package app.sigmatube.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sigmatube.data.VideoItem
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.VideoMenuButton
import app.sigmatube.ui.components.bouncyClickable
import app.sigmatube.ui.components.hqThumbnailUrl
import app.sigmatube.ui.components.shimmer
import app.sigmatube.ui.playlist.PlaylistViewModel
import app.sigmatube.ui.playlist.cleanPlaylistTitle
import app.sigmatube.ui.theme.Accent
import app.sigmatube.util.Format

/** An album opened from the music side: big cover, artist, numbered tracklist. */
@Composable
fun MusicAlbumScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onPlay: (videos: List<VideoItem>, start: Int, shuffle: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks = state.videos
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)
    val rawTitle = state.details?.title.orEmpty()
    val kind = Regex("""^(Album|Single|EP)\b""", RegexOption.IGNORE_CASE).find(rawTitle)?.value?.replaceFirstChar { it.uppercase() } ?: "Album"
    val artist = tracks.mapNotNull { it.channelName?.removeSuffix(" - Topic") }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val totalSeconds = tracks.sumOf { it.durationSeconds.coerceAtLeast(0) }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth()) {
                // Soft glow of the cover behind the header.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, Color.Transparent))),
                )
                Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth()) {
                        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                    }
                    // The tracks' frames are the cover pillarboxed in 16:9: a centre crop gives the square art.
                    val cover = tracks.firstOrNull()?.let { hqThumbnailUrl(it.url) ?: it.thumbnail }
                    if (cover != null) {
                        Artwork(cover, Modifier.size(240.dp).shadow(16.dp, RoundedCornerShape(16.dp)), RoundedCornerShape(16.dp))
                    } else {
                        Box(Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).shimmer())
                    }
                    Spacer(Modifier.height(20.dp))
                    if (state.details != null) {
                        Text(
                            cleanPlaylistTitle(rawTitle),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        artist?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                        Text(
                            Format.meta(kind, if (tracks.isNotEmpty()) "${tracks.size} titre${if (tracks.size > 1) "s" else ""}" else null, totalDuration(totalSeconds)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Box(Modifier.size(200.dp, 24.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .bouncyClickable { if (tracks.isNotEmpty()) onPlay(tracks, 0, true) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Shuffle, "Aléatoire") }
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Accent)
                                .bouncyClickable { if (tracks.isNotEmpty()) onPlay(tracks, 0, false) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PlayArrow, "Tout lire", tint = Color.White, modifier = Modifier.size(36.dp)) }
                        Spacer(Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        when {
            state.isLoading -> items(8) { SongRowSkeleton() }
            state.error != null && tracks.isEmpty() -> item(key = "error") {
                ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.height(320.dp))
            }
            else -> {
                itemsIndexed(tracks, key = { _, v -> v.url }) { i, v -> TrackRow(i + 1, v, artist) { onPlay(tracks, i, false) } }
                if (state.isLoadingMore) item(key = "more") { ListFooterLoader() }
            }
        }
    }
}

/** Track number, title (with the artist only when it differs from the album's), duration, menu. */
@Composable
private fun TrackRow(number: Int, video: VideoItem, albumArtist: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(video.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val artist = video.channelName?.removeSuffix(" - Topic")
            Text(
                Format.meta(artist?.takeIf { it != albumArtist }, Format.duration(video.durationSeconds)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VideoMenuButton(video)
    }
}

private fun totalDuration(seconds: Long): String? {
    if (seconds <= 0) return null
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}
