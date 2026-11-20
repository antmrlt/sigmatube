package app.sigmatube.ui.watch

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sigmatube.data.auth.LikeStatus
import app.sigmatube.data.auth.SortChip
import app.sigmatube.data.auth.YtComment
import app.sigmatube.ui.components.Avatar
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.theme.Accent

/** Everything the comments UI can trigger. */
class CommentActions(
    val loadMore: () -> Unit,
    val sort: (SortChip) -> Unit,
    val toggleReplies: (YtComment) -> Unit,
    val moreReplies: (String) -> Unit,
    val rate: (YtComment, LikeStatus) -> Unit,
)

/** Full, scrollable comments list (panel under the video or side panel in fullscreen). */
@Composable
fun CommentsList(state: CommentsState, actions: CommentActions) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore = actions.loadMore)
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        if (state.sorts.isNotEmpty()) {
            item(key = "sorts") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sorts, key = { it.label }) { chip -> SortChipView(chip) { actions.sort(chip) } }
                }
            }
        }
        when {
            state.isLoading -> item(key = "loading") { ListFooterLoader() }
            state.comments.isEmpty() -> item(key = "empty") {
                Text(
                    if (state.disabled) "Les commentaires sont désactivés." else "Aucun commentaire.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> state.comments.forEach { comment -> thread(comment, state.replies[comment.id], actions) }
        }
        if (state.isLoadingMore) item(key = "more") { ListFooterLoader() }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

private fun LazyListScope.thread(comment: YtComment, replies: RepliesState?, actions: CommentActions) {
    val hasReplies = comment.repliesToken != null
    val expanded = replies?.expanded == true
    item(key = "c:${comment.id}") {
        CommentRow(comment, avatarSize = 28.dp, threadBelow = hasReplies, actions = actions)
    }
    if (expanded) {
        replies?.items?.forEach { reply ->
            item(key = "r:${comment.id}:${reply.id}") {
                ThreadIndent { CommentRow(reply, avatarSize = 20.dp, threadBelow = false, actions = actions) }
            }
        }
    }
    if (hasReplies) {
        item(key = "t:${comment.id}") {
            ThreadIndent {
                Column {
                    when {
                        replies?.loading == true -> CircularProgressIndicator(
                            Modifier.padding(vertical = 8.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        expanded && replies.nextToken != null -> RepliesToggle("Plus de réponses", up = false) {
                            actions.moreReplies(comment.id)
                        }
                        else -> {}
                    }
                    RepliesToggle(
                        label = if (expanded) "Masquer les réponses" else repliesLabel(comment.replyCountText),
                        up = expanded,
                    ) { actions.toggleReplies(comment) }
                }
            }
        }
    }
}

private fun repliesLabel(count: String?): String = when (count) {
    null -> "Réponses"
    "1" -> "1 réponse"
    else -> "$count réponses"
}

/** Indents replies under the parent avatar, with YouTube's vertical thread line. */
@Composable
private fun ThreadIndent(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(start = 4.dp).width(1.dp).height(48.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun RepliesToggle(label: String, up: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Icon(
            if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CommentRow(comment: YtComment, avatarSize: Dp, threadBelow: Boolean, actions: CommentActions) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var expandedText by rememberSaveable(comment.id) { mutableStateOf(false) }
    var overflowing by rememberSaveable(comment.id) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp)) {
        Avatar(comment.authorAvatar, avatarSize)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (comment.isPinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(12.dp), tint = secondary)
                    Text(" Épinglé", style = MaterialTheme.typography.labelSmall, color = secondary)
                }
                Spacer(Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (comment.isCreator) MaterialTheme.colorScheme.onSurface else secondary,
                    fontWeight = if (comment.isCreator) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier
                        .then(
                            if (comment.isCreator) {
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            } else Modifier,
                        ),
                )
                if (comment.isVerified) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(start = 3.dp).size(11.dp), tint = secondary)
                }
                if (comment.publishedText.isNotBlank()) {
                    Text(" • ${comment.publishedText}", style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                comment.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expandedText) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expandedText) overflowing = it.hasVisualOverflow },
                modifier = Modifier
                    .animateContentSize()
                    .clickable(enabled = overflowing || expandedText) { expandedText = !expandedText },
            )
            if (overflowing && !expandedText) {
                Text(
                    "Lire la suite",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                    modifier = Modifier.clickable { expandedText = true }.padding(vertical = 2.dp),
                )
            }
            Row(
                Modifier.padding(top = 2.dp, bottom = if (threadBelow) 0.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RateButton(
                    liked = comment.likeState == LikeStatus.Like,
                    up = true,
                    count = comment.likeCountText,
                ) { actions.rate(comment, LikeStatus.Like) }
                RateButton(liked = comment.likeState == LikeStatus.Dislike, up = false, count = null) {
                    actions.rate(comment, LikeStatus.Dislike)
                }
                if (comment.isHearted) {
                    Box(Modifier.padding(start = 8.dp)) {
                        Avatar(null, 18.dp)
                        Icon(
                            Icons.Rounded.Favorite,
                            "Adoré par le créateur",
                            tint = Accent,
                            modifier = Modifier.align(Alignment.BottomEnd).size(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RateButton(liked: Boolean, up: Boolean, count: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            up && liked -> Icons.Filled.ThumbUp
            up -> Icons.Outlined.ThumbUp
            liked -> Icons.Filled.ThumbDown
            else -> Icons.Outlined.ThumbDown
        }
        Icon(icon, if (up) "J'aime" else "Je n'aime pas", Modifier.size(18.dp))
        if (count != null) {
            Text(
                "  $count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SortChipView(chip: SortChip, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        chip.label,
        style = MaterialTheme.typography.labelLarge,
        color = if (chip.selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (chip.selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** "Commentaires 2,5 M" header text. */
fun commentsTitle(countText: String?) = buildAnnotatedString {
    append("Commentaires")
    if (countText != null) withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append("  $countText") }
}
