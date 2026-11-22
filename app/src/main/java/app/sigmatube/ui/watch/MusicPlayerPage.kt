package app.sigmatube.ui.watch

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.VideoDetails
import app.sigmatube.data.VideoItem
import app.sigmatube.data.auth.LikeStatus
import app.sigmatube.data.withoutHidden
import app.sigmatube.ui.components.EditableVideoRow
import app.sigmatube.ui.components.Motion
import app.sigmatube.ui.components.Thumbnail
import app.sigmatube.ui.components.shimmer
import app.sigmatube.ui.components.userMessage
import app.sigmatube.ui.components.VideoMenuButton
import app.sigmatube.ui.components.bouncyClickable
import app.sigmatube.ui.components.rememberPopScale
import app.sigmatube.ui.components.rememberReorderState
import app.sigmatube.ui.theme.Accent
import app.sigmatube.ui.theme.AppIcons
import app.sigmatube.util.Format

/** Everything the music player page can do. */
class MusicPageActions(
    val minimize: () -> Unit,
    val playPause: () -> Unit,
    val seekTo: (Long) -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val toggleLoop: () -> Unit,
    val shuffle: () -> Unit,
    val like: () -> Unit,
    val saveToPlaylist: () -> Unit,
    val share: () -> Unit,
    val more: () -> Unit,
    val showVideoPage: () -> Unit,
    val channel: (String) -> Unit,
    val openVideo: (VideoItem) -> Unit,
    val queue: QueueActions,
)

/** Height of the page's top bar ("En cours de lecture"). */
val MusicHeaderHeight = 56.dp
/** Side margin of the video, which sits centred like an album cover. */
val MusicVideoMargin = 24.dp

/**
 * Music player page (music videos): header, the video in the middle like an album cover, then
 * title / artist, seek bar, transport controls and what plays next. No description or comments.
 * The video itself is drawn by the overlay over the [videoHeight] gap left here.
 */
@Composable
fun MusicPlayerPage(
    details: VideoDetails,
    state: WatchState,
    ui: PlayerUiState,
    videoHeight: Dp,
    hasNext: Boolean,
    actions: MusicPageActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hidden = LocalHiddenChannels.current
    val queue = state.queue
    val suggestions = remember(details.related, hidden) { details.related.withoutHidden(hidden).filterNot { it.isLive }.take(20) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier.background(colors.background)) {
        // Header: chevron down (minimise), title, overflow menu.
        Row(
            Modifier.fillMaxWidth().height(MusicHeaderHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.minimize) { Icon(Icons.Rounded.KeyboardArrowDown, "Réduire", Modifier.size(30.dp)) }
            Text(
                "En cours de lecture",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.MoreHoriz, "Plus d'options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Afficher la page vidéo") },
                        leadingIcon = { Icon(Icons.Outlined.OndemandVideo, null) },
                        onClick = { menuOpen = false; actions.showVideoPage() },
                    )
                    DropdownMenuItem(
                        text = { Text("Plus d'options…") },
                        leadingIcon = { Icon(AppIcons.MoreVert, null) },
                        onClick = { menuOpen = false; actions.more() },
                    )
                }
            }
        }
        // Room for the video (drawn on top by the overlay).
        Spacer(Modifier.height(videoHeight + 8.dp))

        val listState = rememberLazyListState()
        val reorder = rememberReorderState(
            listState,
            canMoveTo = { (it as? String)?.startsWith("q:") == true },
            onMove = { from, to ->
                val i = queue.indexOfFirst { "q:${it.url}" == from }
                val j = queue.indexOfFirst { "q:${it.url}" == to }
                if (i >= 0 && j >= 0) actions.queue.move(i, j)
            },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "now") {
                Column(Modifier.fillMaxWidth().padding(horizontal = MusicVideoMargin), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        details.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        details.channelName.orEmpty().removeSuffix(" - Topic"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = details.channelUrl != null) { details.channelUrl?.let(actions.channel) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    MusicSeekBar(ui, onSeek = actions.seekTo)
                    Spacer(Modifier.height(12.dp))
                    Controls(state, ui, hasNext, actions)
                    Spacer(Modifier.height(16.dp))
                    SecondaryActions(state, details, actions)
                }
            }

            if (queue.isNotEmpty()) {
                item(key = "queue-title") {
                    SectionHeader("File d'attente", "${queue.size}") {
                        Text(
                            "Tout effacer",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = actions.queue.clear)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                items(queue, key = { "q:${it.url}" }) { video ->
                    val key = "q:${video.url}"
                    EditableVideoRow(
                        video = video,
                        reorder = reorder,
                        key = key,
                        onClick = { actions.queue.play(video) },
                        onRemove = { actions.queue.remove(video) },
                        modifier = if (reorder.draggingKey == key) Modifier else Modifier.animateItem(
                            fadeInSpec = tween(Motion.FADE_MS),
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            fadeOutSpec = tween(120),
                        ),
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                item(key = "suggestions-title") { SectionHeader(if (queue.isEmpty()) "À suivre" else "Ensuite, en lecture auto", null) {} }
                items(suggestions, key = { "s:${it.url}" }) { video ->
                    SuggestionRow(video, onClick = { actions.openVideo(video) })
                }
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun Controls(state: WatchState, ui: PlayerUiState, hasNext: Boolean, actions: MusicPageActions) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlIcon(Icons.Rounded.Shuffle, "Mélanger la file d'attente", enabled = state.queue.size > 1, onClick = actions.shuffle)
        ControlIcon(Icons.Rounded.SkipPrevious, "Précédent", size = 40.dp, onClick = actions.previous)
        // Big accent play / pause, popping on each change.
        val pop = rememberPopScale(ui.isPlaying)
        val pressSource = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(72.dp)
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .clip(CircleShape)
                .background(Accent)
                .clickable(interactionSource = pressSource, indication = null, onClick = actions.playPause),
            contentAlignment = Alignment.Center,
        ) {
            if (ui.isBuffering && !ui.isPlaying) {
                CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ui.isPlaying) "Pause" else "Lecture",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        ControlIcon(Icons.Rounded.SkipNext, "Suivant", size = 40.dp, enabled = hasNext, onClick = actions.next)
        ControlIcon(
            if (state.loop) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            if (state.loop) "Ne plus répéter" else "Répéter",
            tint = if (state.loop) Accent else colors.onSurface,
            onClick = actions.toggleLoop,
        )
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    label: String,
    size: Dp = 28.dp,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Icon(icon, label, Modifier.size(size), tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

/** Like, save to a playlist, share: small round buttons under the controls. */
@Composable
private fun SecondaryActions(state: WatchState, details: VideoDetails, actions: MusicPageActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        val liked = state.likeStatus == LikeStatus.Like
        val likePop = rememberPopScale(liked, enabled = liked)
        SmallAction(
            if (liked) AppIcons.ThumbUpFilled else AppIcons.ThumbUp,
            if (details.likeCount >= 0) Format.compact(details.likeCount + state.likeDelta) else "J'aime",
            iconModifier = Modifier.graphicsLayer { scaleX = likePop; scaleY = likePop },
            onClick = actions.like,
        )
        SmallAction(Icons.AutoMirrored.Outlined.PlaylistAdd, "Enregistrer", onClick = actions.saveToPlaylist)
        SmallAction(AppIcons.Share, "Partager", onClick = actions.share)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, iconModifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .bouncyClickable(pressedScale = 0.92f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, iconModifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, count: String?, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (count != null) {
            Text("  $count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SuggestionRow(video: VideoItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(video, Modifier.width(112.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                Format.meta(video.channelName, Format.duration(video.durationSeconds)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VideoMenuButton(video)
    }
}

/** Thin accent progress line with a round thumb; drag or tap to seek, times underneath. */
@Composable
private fun MusicSeekBar(ui: PlayerUiState, onSeek: (Long) -> Unit) {
    val density = LocalDensity.current
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = ui.duration
    val played = scrub ?: if (duration > 0) (ui.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val thumbSize by animateFloatAsState(if (scrub != null) 18f else 14f, label = "thumb")
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ -> scrub = (change.position.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { scrub?.let { onSeek((it * duration).toLong()) }; scrub = null },
                        onDragCancel = { scrub = null },
                    )
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectTapGestures { onSeek(((it.x / size.width).coerceIn(0f, 1f) * duration).toLong()) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val buffered = if (duration > 0) (ui.buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(track))
            Box(Modifier.fillMaxWidth(buffered).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.fillMaxWidth(played).height(4.dp).clip(CircleShape).background(Accent))
            val x = with(density) { (constraints.maxWidth * played).toDp() } - (thumbSize / 2).dp
            Box(Modifier.offset(x = x).size(thumbSize.dp).background(Accent, CircleShape))
        }
        Row(Modifier.fillMaxWidth()) {
            val shown = scrub?.let { (it * duration).toLong() } ?: ui.position
            Text(Format.position(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                if (duration > 0) Format.position(duration) else "–:––",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The music page while the video loads: same layout as [MusicPlayerPage] (header, cover gap,
 * title, artist, seek bar, controls, actions, next up) with shimmering placeholders.
 */
@Composable
fun MusicPlayerSkeleton(
    videoHeight: Dp,
    error: Throwable?,
    onMinimize: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().height(MusicHeaderHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMinimize) { Icon(Icons.Rounded.KeyboardArrowDown, "Réduire", Modifier.size(30.dp)) }
            Text(
                "En cours de lecture",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(videoHeight + 8.dp))
        if (error != null) {
            Column(
                Modifier.fillMaxWidth().padding(MusicVideoMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    error.userMessageText(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.FilledTonalButton(onClick = onRetry) { Text("Réessayer") }
            }
            return@Column
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = MusicVideoMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Bar(Modifier.fillMaxWidth(0.85f).height(24.dp))
            Spacer(Modifier.height(10.dp))
            Bar(Modifier.fillMaxWidth(0.55f).height(24.dp))
            Spacer(Modifier.height(12.dp))
            Bar(Modifier.fillMaxWidth(0.35f).height(16.dp))
            Spacer(Modifier.height(28.dp))
            Bar(Modifier.fillMaxWidth().height(4.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Bar(Modifier.width(32.dp).height(12.dp))
                Spacer(Modifier.weight(1f))
                Bar(Modifier.width(32.dp).height(12.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(28.dp, 40.dp, 72.dp, 40.dp, 28.dp).forEach { size ->
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(size).clip(CircleShape).shimmer())
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(44.dp).clip(CircleShape).shimmer())
                        Spacer(Modifier.height(6.dp))
                        Bar(Modifier.width(52.dp).height(10.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Bar(Modifier.padding(start = 16.dp).width(110.dp).height(18.dp))
        Spacer(Modifier.height(8.dp))
        // Same shape as the queue / suggestion rows (112dp thumbnails).
        repeat(4) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(10.dp)).shimmer())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Bar(Modifier.fillMaxWidth(0.9f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    Bar(Modifier.fillMaxWidth(0.5f).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun Bar(modifier: Modifier) = Box(modifier.clip(RoundedCornerShape(8.dp)).shimmer())

private fun Throwable.userMessageText(): String = userMessage()
