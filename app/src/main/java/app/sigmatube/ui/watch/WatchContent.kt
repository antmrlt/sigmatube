package app.sigmatube.ui.watch

import app.sigmatube.ui.theme.AppIcons
import app.sigmatube.ui.components.rememberPopScale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sigmatube.data.VideoDetails
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.VideoItem
import app.sigmatube.data.withoutHidden
import app.sigmatube.data.auth.LikeStatus
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import app.sigmatube.ui.components.Avatar
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.Pill
import app.sigmatube.ui.components.VerifiedBadge
import app.sigmatube.ui.components.VideoCard
import app.sigmatube.ui.components.shareVideo
import app.sigmatube.ui.theme.Accent
import app.sigmatube.util.Format

enum class WatchPanel { None, Description, Comments, Queue }

/** What the queue panel can do with the "File d'attente". */
class QueueActions(
    val play: (VideoItem) -> Unit,
    val remove: (VideoItem) -> Unit,
    val move: (Int, Int) -> Unit,
    val shuffle: () -> Unit,
    val clear: () -> Unit,
)

/** Everything below the player: details list, with description/comments panels sliding over it. */
@Composable
fun WatchContent(
    details: VideoDetails,
    comments: CommentsState,
    panel: WatchPanel,
    listState: LazyListState,
    onPanelChange: (WatchPanel) -> Unit,
    commentActions: CommentActions,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
    likeStatus: LikeStatus,
    likeDelta: Long,
    onRate: (LikeStatus) -> Unit,
    inWatchLater: Boolean,
    onToggleWatchLater: () -> Unit,
    queue: List<VideoItem>,
    queueActions: QueueActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Details(
            details = details,
            comments = comments,
            queue = queue,
            onOpenQueue = { onPanelChange(WatchPanel.Queue) },
            likeStatus = likeStatus,
            likeDelta = likeDelta,
            onRate = onRate,
            inWatchLater = inWatchLater,
            onToggleWatchLater = onToggleWatchLater,
            listState = listState,
            onOpenDescription = { onPanelChange(WatchPanel.Description) },
            onOpenComments = { onPanelChange(WatchPanel.Comments) },
            onVideoClick = onVideoClick,
            onChannelClick = onChannelClick,
        )
        // Keep the last opened panel so its content stays visible during the exit animation.
        var lastPanel by remember { mutableStateOf(WatchPanel.Description) }
        if (panel != WatchPanel.None && panel != lastPanel) lastPanel = panel

        AnimatedVisibility(
            visible = panel != WatchPanel.None,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Panel(
                title = when (lastPanel) {
                    WatchPanel.Comments -> "Commentaires"
                    WatchPanel.Queue -> "File d'attente"
                    else -> "Description"
                },
                subtitle = when (lastPanel) {
                    WatchPanel.Comments -> comments.countText
                    WatchPanel.Queue -> queue.size.takeIf { it > 0 }?.toString()
                    else -> null
                },
                onClose = { onPanelChange(WatchPanel.None) },
            ) {
                when (lastPanel) {
                    WatchPanel.Comments -> CommentsList(comments, commentActions, details.channelAvatar)
                    WatchPanel.Queue -> QueueContent(
                        queue = queue,
                        onPlay = queueActions.play,
                        onRemove = queueActions.remove,
                        onMove = queueActions.move,
                        onShuffle = queueActions.shuffle,
                        onClear = { queueActions.clear(); onPanelChange(WatchPanel.None) },
                    )
                    else -> DescriptionContent(details, onChannelClick)
                }
            }
        }
    }
}


@Composable
private fun Panel(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val closeThreshold = with(density) { 120.dp.toPx() }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset }
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header doubles as a drag handle: pull down to dismiss.
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > closeThreshold) onClose()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onVerticalDrag = { _, dy -> dragOffset = (dragOffset + dy).coerceAtLeast(0f) },
                    )
                },
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape),
            )
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontSize = 20.sp)
                if (subtitle != null) {
                    Text(
                        "  $subtitle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fermer") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        content()
    }
}

@Composable
private fun Details(
    details: VideoDetails,
    comments: CommentsState,
    queue: List<VideoItem>,
    onOpenQueue: () -> Unit,
    likeStatus: LikeStatus,
    likeDelta: Long,
    onRate: (LikeStatus) -> Unit,
    inWatchLater: Boolean,
    onToggleWatchLater: () -> Unit,
    listState: LazyListState,
    onOpenDescription: () -> Unit,
    onOpenComments: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val hidden = LocalHiddenChannels.current

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "title") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDescription)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(details.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = secondary)) {
                            append(
                                listOfNotNull(
                                    Format.views(details.viewCount),
                                    if (details.isLive) "En direct" else Format.relative(details.uploadDate, details.uploadedText),
                                ).joinToString("  "),
                            )
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("  …plus") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item(key = "channel") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = details.channelUrl != null) { details.channelUrl?.let(onChannelClick) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(details.channelAvatar, 40.dp)
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        details.channelName.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (details.channelVerified) VerifiedBadge(Modifier.padding(start = 4.dp))
                }
                Format.subscribers(details.subscriberCount)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item(key = "actions") {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LikePill(
                    likeCount = if (details.likeCount >= 0) details.likeCount + likeDelta else -1,
                    status = likeStatus,
                    onRate = onRate,
                )
                Pill(onClick = { shareVideo(context, details.title, details.url) }) {
                    Icon(AppIcons.Reply, null, Modifier.size(20.dp).graphicsLayer(scaleX = -1f))
                    Text("Partager", style = MaterialTheme.typography.labelLarge)
                }
                Pill(onClick = onToggleWatchLater) {
                    val savePop = rememberPopScale(inWatchLater, enabled = inWatchLater)
                    Icon(
                        if (inWatchLater) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                        null,
                        Modifier.size(20.dp).graphicsLayer { scaleX = savePop; scaleY = savePop },
                    )
                    Text(if (inWatchLater) "Enregistrée" else "Enregistrer", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (queue.isNotEmpty()) {
            item(key = "queue") { QueueTeaser(queue, onOpenQueue) }
        }

        if (!comments.disabled && (comments.isLoading || comments.comments.isNotEmpty())) {
            item(key = "comments") { CommentsTeaser(comments, onOpenComments) }
        }

        items(details.related.withoutHidden(hidden), key = { it.url }) { video ->
            VideoCard(
                video = video,
                onClick = { onVideoClick(video) },
                onChannelClick = video.channelUrl?.let { url -> { onChannelClick(url) } },
            )
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun CommentsTeaser(comments: CommentsState, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Commentaires", style = MaterialTheme.typography.titleSmall)
            if (comments.countText != null) {
                Text(
                    "  ${comments.countText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val first = comments.comments.firstOrNull()
        if (first != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(first.authorAvatar, 24.dp)
                Spacer(Modifier.width(10.dp))
                Text(first.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DescriptionContent(details: VideoDetails, onChannelClick: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(details.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            Row(
                Modifier
                    .clickable(enabled = details.channelUrl != null) { details.channelUrl?.let(onChannelClick) }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(details.channelAvatar, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(details.channelName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                if (details.channelVerified) VerifiedBadge(Modifier.padding(start = 4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                Stat(if (details.likeCount >= 0) Format.compact(details.likeCount) else "–", "J'aime", Modifier.weight(1f))
                Stat(if (details.viewCount >= 0) Format.compact(details.viewCount) else "–", "Vues", Modifier.weight(1f))
                Stat(
                    details.uploadDate?.year?.toString() ?: "–",
                    details.uploadDate?.let { "${it.dayOfMonth} ${MONTHS[it.monthValue - 1]}" } ?: "",
                    Modifier.weight(1f),
                )
            }
            if (details.description.isNotBlank()) {
                SelectionContainer {
                    Text(
                        details.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** Like | dislike split pill; filled icons reflect the user's rating. */
@Composable
private fun LikePill(likeCount: Long, status: LikeStatus, onRate: (LikeStatus) -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable { onRate(LikeStatus.Like) }
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val likePop = rememberPopScale(status, enabled = status == LikeStatus.Like)
            Icon(
                if (status == LikeStatus.Like) AppIcons.ThumbUpFilled else AppIcons.ThumbUp,
                if (status == LikeStatus.Like) "Je n'aime plus" else "J'aime",
                Modifier.size(20.dp).graphicsLayer { scaleX = likePop; scaleY = likePop; rotationZ = (likePop - 1f) * -40f },
            )
            if (likeCount >= 0) Text(Format.compact(likeCount), style = MaterialTheme.typography.labelLarge)
        }
        VerticalDivider(Modifier.height(20.dp), color = MaterialTheme.colorScheme.outline)
        Box(
            Modifier
                .fillMaxHeight()
                .clickable { onRate(LikeStatus.Dislike) }
                .padding(start = 10.dp, end = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dislikePop = rememberPopScale(status, enabled = status == LikeStatus.Dislike)
            Icon(
                if (status == LikeStatus.Dislike) AppIcons.ThumbDownFilled else AppIcons.ThumbDown,
                "Je n'aime pas",
                Modifier.size(20.dp).graphicsLayer { scaleX = dislikePop; scaleY = dislikePop },
            )
        }
    }
}
