package app.sigmatube.ui.watch

import app.sigmatube.ui.theme.AppIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.sigmatube.ui.components.rememberPopScale
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer

/** Everything the comments UI can trigger. */
class CommentActions(
    val loadMore: () -> Unit,
    val sort: (SortChip) -> Unit,
    val openReplies: (YtComment) -> Unit,
    val moreReplies: (String) -> Unit,
    val rate: (YtComment, LikeStatus) -> Unit,
)

private val CommentStart = 16.dp
private val AvatarSize = 24.dp
private val ReplyAvatarSize = 20.dp
private val AvatarGap = 12.dp

/** Where a comment's text starts: replies and the "N réponses" link line up with it. */
private val TextStart = CommentStart + AvatarSize + AvatarGap

/**
 * Comments (panel under the video or side panel in fullscreen). Like the YouTube app,
 * tapping "N réponses" slides to a replies page for that thread.
 */
@Composable
fun CommentsList(state: CommentsState, actions: CommentActions, creatorAvatar: String?) {
    val listState = rememberLazyListState()
    var openThreadId by rememberSaveable { mutableStateOf<String?>(null) }
    val thread = openThreadId?.let { id -> state.comments.firstOrNull { it.id == id } }
    // Another video / sort order: the open thread no longer exists.
    LaunchedEffect(thread == null) { if (thread == null) openThreadId = null }
    BackHandler(enabled = thread != null) { openThreadId = null }

    AnimatedContent(
        targetState = thread,
        contentKey = { it?.id },
        transitionSpec = {
            if (targetState != null) slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
            else slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
        },
        label = "comments",
        modifier = Modifier.fillMaxSize(),
    ) { open ->
        if (open == null) {
            ThreadsList(state, actions, creatorAvatar, listState) { comment ->
                actions.openReplies(comment)
                openThreadId = comment.id
            }
        } else {
            RepliesPage(
                comment = open,
                replies = state.replies[open.id] ?: RepliesState(loading = true),
                actions = actions,
                creatorAvatar = creatorAvatar,
                onBack = { openThreadId = null },
            )
        }
    }
}

@Composable
private fun ThreadsList(
    state: CommentsState,
    actions: CommentActions,
    creatorAvatar: String?,
    listState: LazyListState,
    onOpenReplies: (YtComment) -> Unit,
) {
    LoadMoreEffect(listState, onLoadMore = actions.loadMore)
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        if (state.sorts.isNotEmpty()) {
            item(key = "sorts") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sorts, key = { it.token }) { chip -> SortChipView(chip) { actions.sort(chip) } }
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
            else -> items(state.comments, key = { "c:${it.id}" }) { comment ->
                Column {
                    CommentRow(comment, AvatarSize, creatorAvatar, actions)
                    if (comment.repliesToken != null) {
                        Text(
                            repliesLabel(comment.replyCountText),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .padding(start = TextStart - 8.dp)
                                .clip(CircleShape)
                                .clickable { onOpenReplies(comment) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (state.isLoadingMore) item(key = "more") { ListFooterLoader() }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

/** One thread: the parent comment on top, its replies below (10 at a time). */
@Composable
private fun RepliesPage(
    comment: YtComment,
    replies: RepliesState,
    actions: CommentActions,
    creatorAvatar: String?,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState) { actions.moreReplies(comment.id) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour aux commentaires") }
            Text("Réponses", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "parent") {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(bottom = 8.dp)) {
                    CommentRow(comment, AvatarSize, creatorAvatar, actions, startExpanded = true)
                }
            }
            items(replies.items, key = { "r:${it.id}" }) { reply ->
                CommentRow(reply, ReplyAvatarSize, creatorAvatar, actions, start = TextStart)
            }
            if (replies.loading) item(key = "loading") { ListFooterLoader() }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

private fun repliesLabel(count: String?): String = when (count) {
    null -> "Réponses"
    "1" -> "1 réponse"
    else -> "$count réponses"
}

@Composable
private fun CommentRow(
    comment: YtComment,
    avatarSize: Dp,
    creatorAvatar: String?,
    actions: CommentActions,
    start: Dp = CommentStart,
    startExpanded: Boolean = false,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var expandedText by rememberSaveable(comment.id, startExpanded) { mutableStateOf(startExpanded) }
    var overflowing by rememberSaveable(comment.id, startExpanded) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(start = start, end = 16.dp, top = 12.dp)) {
        Avatar(comment.authorAvatar, avatarSize)
        Spacer(Modifier.width(AvatarGap))
        Column(Modifier.weight(1f)) {
            if (comment.isPinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(12.dp), tint = secondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Épinglé par le créateur", style = MaterialTheme.typography.labelSmall, color = secondary)
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (comment.isCreator) MaterialTheme.colorScheme.onSurface else secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (comment.isCreator) {
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            } else Modifier,
                        ),
                )
                if (comment.isVerified) {
                    Icon(Icons.Rounded.CheckCircle, "Validé", Modifier.padding(start = 3.dp).size(11.dp), tint = secondary)
                }
                if (comment.publishedText.isNotBlank()) {
                    Text(
                        " • ${comment.publishedText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                comment.text.trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expandedText) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expandedText) overflowing = it.hasVisualOverflow },
                modifier = Modifier
                    .animateContentSize()
                    .clickable(enabled = overflowing || expandedText, indication = null, interactionSource = null) {
                        expandedText = !expandedText
                    },
            )
            if (overflowing && !expandedText) {
                Text(
                    "Lire la suite",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = secondary,
                    modifier = Modifier.clickable(indication = null, interactionSource = null) { expandedText = true }.padding(top = 2.dp),
                )
            }
            // Like · dislike · creator heart, pulled left by the buttons' touch padding so icons align with the text.
            Row(Modifier.offset(x = (-8).dp), verticalAlignment = Alignment.CenterVertically) {
                RateButton(
                    liked = comment.likeState == LikeStatus.Like,
                    up = true,
                    count = comment.likeCountText,
                ) { actions.rate(comment, LikeStatus.Like) }
                RateButton(liked = comment.likeState == LikeStatus.Dislike, up = false, count = null) {
                    actions.rate(comment, LikeStatus.Dislike)
                }
                if (comment.isHearted) {
                    Box(Modifier.padding(start = 8.dp).size(22.dp)) {
                        Avatar(creatorAvatar, 18.dp)
                        Icon(
                            Icons.Rounded.Favorite,
                            "Adoré par le créateur",
                            tint = Accent,
                            modifier = Modifier.align(Alignment.BottomEnd).size(11.dp),
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
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            up && liked -> AppIcons.ThumbUpFilled
            up -> AppIcons.ThumbUp
            liked -> AppIcons.ThumbDownFilled
            else -> AppIcons.ThumbDown
        }
        val pop = rememberPopScale(liked, enabled = liked)
        Icon(icon, if (up) "J'aime" else "Je n'aime pas", Modifier.size(16.dp).graphicsLayer { scaleX = pop; scaleY = pop })
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SortChipView(chip: SortChip, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (chip.selected) colors.primary else colors.surfaceContainerHigh, label = "sortBg")
    val fg by animateColorAsState(if (chip.selected) colors.onPrimary else colors.onSurface, label = "sortFg")
    Text(
        chip.label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** "Commentaires 2,5 M" header text. */
fun commentsTitle(countText: String?) = buildAnnotatedString {
    append("Commentaires")
    if (countText != null) withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append("  $countText") }
}
