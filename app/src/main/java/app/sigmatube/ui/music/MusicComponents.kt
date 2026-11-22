package app.sigmatube.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sigmatube.data.VideoItem
import app.sigmatube.data.auth.MusicCard
import app.sigmatube.ui.components.VideoMenuButton
import app.sigmatube.ui.components.bouncyClickable
import app.sigmatube.ui.components.shimmer
import app.sigmatube.util.Format
import coil3.compose.AsyncImage

/** Cropped artwork (album cover, artist photo, video frame). */
@Composable
fun Artwork(url: String?, modifier: Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

/** Song row: square artwork, title, artist · duration, menu. */
@Composable
fun SongRow(video: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier, rank: Int? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                "$rank",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Artwork(video.thumbnail, Modifier.size(52.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Format.meta(video.channelName, video.viewsText, Format.duration(video.durationSeconds)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VideoMenuButton(video)
    }
}

/** Placeholder rows while songs load. */
@Composable
fun SongRowSkeleton() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).shimmer())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.8f).height(13.dp).clip(RoundedCornerShape(7.dp)).shimmer())
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        }
    }
}

/** Round artist photo with the name under it. */
@Composable
fun ArtistCircle(name: String, thumbnail: String?, onClick: () -> Unit, size: Dp = 96.dp) {
    Column(
        Modifier.width(size + 8.dp).bouncyClickable(pressedScale = 0.94f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(thumbnail, Modifier.size(size), CircleShape)
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** A row of YouTube Music tiles: circles for artists, 16:9 for videos, squares for albums / playlists. */
@Composable
fun MusicCardsRow(cards: List<MusicCard>, onClick: (MusicCard) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = { it.videoId ?: it.albumId ?: it.artistId ?: it.playlistId ?: it.title }) { card ->
            when {
                card.artistId != null && card.albumId == null -> ArtistCircle(card.title, card.thumbnail, onClick = { onClick(card) })
                card.videoId != null -> Column(Modifier.width(240.dp).bouncyClickable(pressedScale = 0.95f) { onClick(card) }) {
                    Artwork(card.thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(card.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    card.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                else -> Column(Modifier.width(150.dp).bouncyClickable(pressedScale = 0.95f) { onClick(card) }) {
                    Artwork(card.thumbnail, Modifier.size(150.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(card.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    card.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Section title of the music pages. */
@Composable
fun MusicSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp))
}

/** A video tile as a playable list item. */
fun MusicCard.toVideoItem(artist: String?): VideoItem? = videoId?.let {
    VideoItem(
        url = "https://www.youtube.com/watch?v=$it",
        title = title,
        thumbnail = thumbnail,
        channelName = artist ?: subtitle,
        channelUrl = null,
        channelAvatar = null,
        durationSeconds = -1,
        viewCount = -1,
        uploadDate = null,
        uploadedText = null,
        isLive = false,
    )
}
