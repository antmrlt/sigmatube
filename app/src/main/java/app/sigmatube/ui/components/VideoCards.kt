package app.sigmatube.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sigmatube.data.VideoItem
import app.sigmatube.data.LocalWatchProgress
import app.sigmatube.data.videoIdOf
import androidx.compose.foundation.layout.fillMaxHeight
import app.sigmatube.data.auth.PlaylistItem
import app.sigmatube.ui.theme.Accent
import app.sigmatube.util.Format
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter

@Composable
fun Thumbnail(video: VideoItem, modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        // Ask for the 1280x720 frame; fall back to whatever the API gave if it doesn't exist.
        val fallback = rememberAsyncImagePainter(video.thumbnail)
        AsyncImage(
            model = remember(video.url) { hqThumbnailUrl(video.url) } ?: video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = fallback,
            modifier = Modifier.matchParentSize(),
        )
        // Watched fraction: from this device first, otherwise what the account feed reported.
        val progress = LocalWatchProgress.current[videoIdOf(video.url)] ?: video.watchedFraction
        val badge = if (video.isLive) "EN DIRECT" else Format.duration(video.durationSeconds)
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .padding(bottom = if (progress != null && progress > 0f) 4.dp else 0.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (video.isLive) Accent else Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (progress != null && progress > 0f && !video.isLive) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.35f)),
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0.03f, 1f)).fillMaxHeight().background(Accent))
            }
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

fun shareVideo(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Overflow (⋮) button with per-video actions. */
@Composable
fun VideoMenuButton(video: VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.MoreVert, "Plus d'options", Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Partager") },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = {
                    open = false
                    shareVideo(context, video.title, video.url)
                },
            )
        }
    }
}

/** Full-bleed feed card: edge-to-edge thumbnail, then avatar + title + meta + overflow. */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 20.dp),
    ) {
        Thumbnail(video, Modifier.fillMaxWidth())
        Row(Modifier.padding(start = 12.dp, top = 12.dp)) {
            if (onChannelClick != null) {
                Avatar(video.channelAvatar, 36.dp, Modifier.clickable(onClick = onChannelClick))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    Format.meta(
                        video.channelName,
                        Format.views(video.viewCount) ?: video.viewsText,
                        Format.relative(video.uploadDate, video.uploadedText),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VideoMenuButton(video, Modifier.padding(end = 4.dp))
        }
    }
}

/** Compact horizontal row (used in landscape / dense lists). */
@Composable
fun VideoRow(video: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(video, Modifier.width(160.dp), RoundedCornerShape(8.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                Format.meta(video.channelName, Format.views(video.viewCount) ?: video.viewsText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VideoMenuButton(video)
    }
}

/** Compact card for horizontal rows (most relevant, channel shelves). */
@Composable
fun VideoShelfCard(video: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(260.dp).clickable(onClick = onClick)) {
        Thumbnail(video, Modifier.fillMaxWidth(), RoundedCornerShape(12.dp))
        Spacer(Modifier.height(8.dp))
        Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            Format.meta(video.channelName, Format.views(video.viewCount) ?: video.viewsText, video.uploadedText),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Playlist thumbnail with its video-count badge, then the title. */
@Composable
fun PlaylistCard(playlist: PlaylistItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(playlist.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (playlist.countText != null) {
                Text(
                    playlist.countText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Small rounded "pill" used for action rows (like, share…). */
@Composable
fun Pill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    filled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Rounded.CheckCircle,
        "Vérifiée",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(14.dp),
    )
}

/** High-resolution 16:9 thumbnail (no letterboxing), available for most videos. */
fun hqThumbnailUrl(videoUrl: String): String? =
    videoIdOf(videoUrl)?.let { "https://i.ytimg.com/vi/$it/hq720.jpg" }
