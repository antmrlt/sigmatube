package app.sigmatube.ui.watch

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.view.ViewTreeObserver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sigmatube.data.VideoItem
import app.sigmatube.ui.components.Thumbnail
import app.sigmatube.ui.components.shareVideo
import app.sigmatube.ui.components.LocalVideoMenu
import app.sigmatube.ui.components.shimmer
import app.sigmatube.data.auth.LikeStatus
import app.sigmatube.ui.components.userMessage
import app.sigmatube.ui.theme.Accent
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** 0 = expanded (full page), 1 = minimized (mini-player bar). */
@Stable
class WatchSheetState {
    var progress by mutableFloatStateOf(0f)
        internal set

    val isMinimized: Boolean get() = progress >= 0.999f

    /** Bumped to ask the watch page to open its queue panel (e.g. from the Musique tab). */
    var queueRequests by mutableIntStateOf(0)

    suspend fun expand() = animateTo(0f)
    suspend fun minimize() = animateTo(1f)

    suspend fun animateTo(target: Float, velocity: Float = 0f) {
        animate(progress, target, velocity, spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> progress = v }
    }
}

private enum class Corner { TopStart, TopEnd, BottomStart, BottomEnd }

private val MiniMaxWidth = 220.dp
private val MiniMargin = 12.dp

@Composable
fun WatchOverlay(
    viewModel: WatchViewModel,
    sheet: WatchSheetState,
    /** Space to keep clear at the bottom for the floating player (bottom nav bar, or system nav bar). */
    bottomInset: Dp,
    onOpenVideo: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onLoginRequired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ui = rememberPlayerUiState(viewModel.player)
    val commentActions = remember(viewModel) {
        CommentActions(
            loadMore = viewModel::loadMoreComments,
            sort = viewModel::sortComments,
            openReplies = viewModel::openReplies,
            moreReplies = viewModel::loadMoreReplies,
            rate = { comment, target ->
                if (!viewModel.rateComment(comment, target)) {
                    Toast.makeText(context, "Connecte-toi pour noter les commentaires", Toast.LENGTH_SHORT).show()
                    onLoginRequired()
                }
            },
        )
    }
    val details = state.details
    val queueActions = remember(viewModel) {
        QueueActions(
            play = { viewModel.playFromQueue(it.url) },
            remove = { viewModel.removeFromQueue(it.url) },
            move = viewModel::moveInQueue,
            shuffle = viewModel::shuffleQueue,
            clear = viewModel::clearQueue,
        )
    }

    var panel by remember { mutableStateOf(WatchPanel.None) }
    var showSettings by remember { mutableStateOf(false) }
    var showRecommendations by remember { mutableStateOf(false) }
    var showSideComments by remember { mutableStateOf(false) }
    var openRecommendationsRequest by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val playerSettings by viewModel.settingsState.collectAsStateWithLifecycle()
    var openCommentsRequest by remember { mutableStateOf(0) }
    val related = details?.related.orEmpty()
    fun loginToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        onLoginRequired()
    }
    val playerActions = PlayerActions(
        channel = details?.channelName,
        autoplay = playerSettings.autoplay,
        onAutoplay = viewModel::setAutoplay,
        captionsOn = playerSettings.captions,
        onCaptions = viewModel::toggleCaptions,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        hasNext = state.queue.isNotEmpty() || related.any { !it.isLive },
        likeStatus = state.likeStatus,
        onLike = { if (!viewModel.toggleRating(LikeStatus.Like)) loginToast("Connecte-toi pour noter les vidéos") },
        onDislike = { if (!viewModel.toggleRating(LikeStatus.Dislike)) loginToast("Connecte-toi pour noter les vidéos") },
        onComments = { openCommentsRequest++ },
        inWatchLater = state.inWatchLater,
        onSave = { if (!viewModel.toggleWatchLater()) loginToast("Connecte-toi pour enregistrer des vidéos") },
        onShare = { details?.let { shareVideo(context, it.title, it.url) } },
        nextThumbnail = state.queue.firstOrNull()?.thumbnail ?: related.firstOrNull()?.thumbnail,
        onMoreVideos = { openRecommendationsRequest++ },
    )

    // Floating mini-player: which corner it rests in, and the live drag offset from that corner.
    var corner by remember { mutableStateOf(Corner.BottomEnd) }
    var largeMini by remember { mutableStateOf(false) }
    val largeMiniFraction by animateFloatAsState(if (largeMini) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "largeMini")
    val haptics = LocalHapticFeedback.current
    val floatOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // A new video: back to the top, panels closed.
    LaunchedEffect(state.url) {
        // The queue panel stays open while going through the queue.
        if (panel != WatchPanel.Queue || state.queue.isEmpty()) panel = WatchPanel.None
        showRecommendations = false
        floatOffset.snapTo(Offset.Zero)
        listState.scrollToItem(0)
    }
    // Only requests made while this page is shown (not one left over from a previous video).
    val handledQueueRequests = remember { intArrayOf(sheet.queueRequests) }
    LaunchedEffect(sheet.queueRequests) {
        if (sheet.queueRequests > handledQueueRequests[0]) panel = WatchPanel.Queue
        handledQueueRequests[0] = sheet.queueRequests
    }
    LaunchedEffect(openCommentsRequest) {
        if (openCommentsRequest == 0) return@LaunchedEffect
        if (isLandscape) { showRecommendations = false; showSideComments = !showSideComments } else panel = WatchPanel.Comments
    }
    LaunchedEffect(openRecommendationsRequest) {
        if (openRecommendationsRequest > 0 && isLandscape) { showSideComments = false; showRecommendations = true }
    }
    LaunchedEffect(isLandscape) {
        if (isLandscape) sheet.progress = 0f else { showRecommendations = false; showSideComments = false }
    }
    // No background playback yet.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppBackgrounded() }

    fun setFullscreen(on: Boolean) {
        activity?.requestedOrientation = if (on) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }
    fun minimize() = scope.launch { panel = WatchPanel.None; sheet.minimize() }
    fun close() {
        viewModel.close()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Started from the Musique tab: music player page (portrait only), video centred, no comments.
    val musicView = state.musicView && !isLandscape
    val videoMenu = LocalVideoMenu.current
    LaunchedEffect(musicView) { if (musicView) panel = WatchPanel.None }

    // The music page is light/dark like the app, the video page has a black band behind the status bar.
    SystemBarsEffect(darkStatusBar = sheet.progress < 0.5f && !musicView, immersive = isLandscape)

    BackHandler(enabled = !sheet.isMinimized) {
        when {
            showRecommendations -> showRecommendations = false
            showSideComments -> showSideComments = false
            isLandscape -> setFullscreen(false)
            panel != WatchPanel.None -> panel = WatchPanel.None
            else -> minimize()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val statusTop = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
        val margin = with(density) { MiniMargin.toPx() }
        val bottomPx = with(density) { bottomInset.toPx() }
        val thresholdPx = with(density) { 72.dp.toPx() }
        val p = sheet.progress

        // Geometry: expanded video rect -> floating rect in the current corner.
        // Expanded video rect: full width at the top, or inset under the header on the music page.
        val musicMarginPx = with(density) { MusicVideoMargin.toPx() }
        val expandedX = if (musicView) musicMarginPx else 0f
        val expandedY = if (musicView) statusTop + with(density) { MusicHeaderHeight.toPx() } else statusTop
        val expandedW = if (musicView) w - 2 * musicMarginPx else w
        val expandedH = expandedW * 9f / 16f
        // Long press on the floating player toggles a large, full-width version.
        val smallMiniW = minOf(w * 0.5f, with(density) { MiniMaxWidth.toPx() })
        val miniW = lerpF(smallMiniW, w - 2 * margin, largeMiniFraction)
        val miniH = miniW * 9f / 16f
        fun cornerPos(c: Corner) = Offset(
            x = if (c == Corner.TopEnd || c == Corner.BottomEnd) w - miniW - margin else margin,
            y = if (c == Corner.BottomStart || c == Corner.BottomEnd) h - bottomPx - miniH - margin else statusTop + margin,
        )
        val target = cornerPos(corner) + floatOffset.value
        val travelPx = (target.y - statusTop).coerceAtLeast(1f)

        var upDrag by remember { mutableFloatStateOf(0f) }
        val dragState = rememberDraggableState { delta ->
            when {
                isLandscape -> upDrag += delta
                sheet.progress <= 0f && delta < 0 || upDrag < 0 -> upDrag = (upDrag + delta).coerceAtMost(0f)
                else -> sheet.progress = (sheet.progress + delta / travelPx).coerceIn(0f, 1f)
            }
        }
        // Vertical swipes on the expanded / fullscreen player.
        val expandedDrag = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStarted = { panel = WatchPanel.None },
            onDragStopped = { velocity ->
                when {
                    isLandscape -> {
                        if (upDrag < -thresholdPx) showRecommendations = true
                        else if (upDrag > thresholdPx) {
                            if (showRecommendations) showRecommendations = false else setFullscreen(false)
                        }
                    }
                    upDrag < 0 -> if (upDrag < -thresholdPx) setFullscreen(true)
                    else -> {
                        val to = when {
                            velocity > 1500f -> 1f
                            velocity < -1500f -> 0f
                            else -> if (sheet.progress > 0.4f) 1f else 0f
                        }
                        sheet.animateTo(to, velocity / travelPx)
                    }
                }
                upDrag = 0f
            },
        )

        if (isLandscape) {
            Row(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.weight(1f).fillMaxHeight().then(expandedDrag)) {
                    PlayerSlot(viewModel, state, ui, interactive = true, isFullscreen = true,
                        onToggleFullscreen = { setFullscreen(false) }, onMinimize = { setFullscreen(false) },
                        onSettings = { showSettings = true },
                        actions = playerActions,
                        modifier = Modifier.fillMaxSize())
                    FullscreenRecommendations(
                        visible = showRecommendations && !showSideComments,
                        videos = details?.related.orEmpty(),
                        onClick = { showRecommendations = false; onOpenVideo(it.url) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                // YouTube-style side panel: the video shrinks to the left, comments on the right.
                AnimatedVisibility(
                    visible = showSideComments,
                    enter = expandHorizontally(expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    SideCommentsPanel(
                        comments = state.comments,
                        actions = commentActions,
                        creatorAvatar = details?.channelAvatar,
                        onClose = { showSideComments = false },
                        modifier = Modifier.fillMaxHeight().width(with(density) { (w * 0.4f).toDp() }),
                    )
                }
            }
            return@BoxWithConstraints
        }

        // The watch page behind the video: fades and slides away as the video shrinks.
        if (p < 0.999f) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - p * 1.4f).coerceIn(0f, 1f)
                        translationY = p * h * 0.25f
                    }
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (musicView && details == null) {
                    Spacer(Modifier.fillMaxWidth().height(with(density) { statusTop.toDp() }))
                    MusicPlayerSkeleton(
                        videoHeight = with(density) { expandedH.toDp() },
                        error = state.error,
                        onMinimize = { minimize() },
                        onRetry = viewModel::retry,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else if (musicView && details != null) {
                    Spacer(Modifier.fillMaxWidth().height(with(density) { statusTop.toDp() }))
                    MusicPlayerPage(
                        details = details,
                        state = state,
                        ui = ui,
                        videoHeight = with(density) { expandedH.toDp() },
                        hasNext = playerActions.hasNext,
                        actions = MusicPageActions(
                            minimize = { minimize() },
                            playPause = { val pl = viewModel.player; if (pl.isPlaying) pl.pause() else { if (pl.playbackState == androidx.media3.common.Player.STATE_ENDED) pl.seekTo(0); pl.play() } },
                            seekTo = { viewModel.player.seekTo(it) },
                            previous = viewModel::previous,
                            next = viewModel::next,
                            toggleLoop = { viewModel.setLoop(!state.loop) },
                            shuffle = viewModel::shuffleQueue,
                            like = playerActions.onLike,
                            saveToPlaylist = { viewModel.currentItem()?.let { videoMenu?.saveToPlaylist(it) } },
                            share = playerActions.onShare,
                            more = { viewModel.currentItem()?.let { videoMenu?.open(it) } },
                            showVideoPage = { viewModel.setMusicView(false) },
                            channel = { url -> minimize(); onChannelClick(url) },
                            openVideo = { onOpenVideo(it.url) },
                            queue = queueActions,
                        ),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                Spacer(Modifier.fillMaxWidth().height(with(density) { (statusTop + expandedH).toDp() }).background(Color.Black))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        details != null -> WatchContent(
                            details = details,
                            comments = state.comments,
                            panel = panel,
                            listState = listState,
                            onPanelChange = { panel = it },
                            commentActions = commentActions,
                            onVideoClick = { onOpenVideo(it.url) },
                            onChannelClick = { url -> minimize(); onChannelClick(url) },
                            likeStatus = state.likeStatus,
                            likeDelta = state.likeDelta,
                            onRate = { target ->
                                if (!viewModel.toggleRating(target)) {
                                    Toast.makeText(context, "Connecte-toi pour noter les vidéos", Toast.LENGTH_SHORT).show()
                                    minimize()
                                    onLoginRequired()
                                }
                            },
                            queue = state.queue,
                            queueActions = queueActions,
                            inWatchLater = state.inWatchLater,
                            onToggleWatchLater = {
                                if (!viewModel.toggleWatchLater()) {
                                    Toast.makeText(context, "Connecte-toi pour enregistrer des vidéos", Toast.LENGTH_SHORT).show()
                                    minimize()
                                    onLoginRequired()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        state.error != null -> Text(
                            state.error!!.userMessage(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                        else -> CircularProgressIndicator(Modifier.align(Alignment.Center), strokeWidth = 3.dp)
                    }
                }
                }
            }
        }

        // The video itself, interpolated between its expanded slot and the floating corner.
        val x = lerpF(expandedX, target.x, p)
        val y = lerpF(expandedY, target.y, p)
        val vw = lerpF(expandedW, miniW, p)
        val vh = lerpF(expandedH, miniH, p)
        val minimized = sheet.isMinimized

        val floatingGestures = Modifier
            .pointerInput(corner, w, h, bottomPx, miniW) {
                val tracker = VelocityTracker()
                // The box moves under the finger, so local positions barely change: track the
                // accumulated drag instead to get a real fling velocity.
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { tracker.resetTracking(); total = Offset.Zero },
                    onDrag = { change, amount ->
                        total += amount
                        tracker.addPosition(change.uptimeMillis, total)
                        scope.launch { floatOffset.snapTo(floatOffset.value + amount) }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        val pos = cornerPos(corner) + floatOffset.value
                        // Where a fling would carry it.
                        val projected = pos + Offset(v.x, v.y) * 0.2f
                        val cx = projected.x + miniW / 2
                        val cy = projected.y + miniH / 2
                        scope.launch {
                            if (cx < 0f || cx > w || cy > h) {
                                // Thrown off-screen: slide out and close.
                                val base = cornerPos(corner)
                                val outX = when {
                                    cx < 0f -> -miniW - margin - base.x
                                    cx > w -> w + margin - base.x
                                    else -> floatOffset.value.x
                                }
                                val outY = if (cy > h) h - base.y else floatOffset.value.y
                                floatOffset.animateTo(Offset(outX, outY), tween(180))
                                close()
                            } else {
                                val newCorner = when {
                                    cx < w / 2 && cy < h / 2 -> Corner.TopStart
                                    cy < h / 2 -> Corner.TopEnd
                                    cx < w / 2 -> Corner.BottomStart
                                    else -> Corner.BottomEnd
                                }
                                floatOffset.snapTo(pos - cornerPos(newCorner))
                                corner = newCorner
                                floatOffset.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { scope.launch { sheet.expand() } },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        largeMini = !largeMini
                    },
                )
            }

        val radius = lerp(if (musicView) 16.dp else 0.dp, 12.dp, p)
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(with(density) { vw.toDp() }, with(density) { vh.toDp() })
                .shadow(lerp(if (musicView) 12.dp else 0.dp, 8.dp, p), RoundedCornerShape(radius))
                // Expanded video page: no clip, so the seek bar thumb can overhang the bottom edge like YouTube.
                .then(if (p > 0f || musicView) Modifier.clip(RoundedCornerShape(radius)) else Modifier)
                .background(Color.Black)
                .then(if (minimized) floatingGestures else expandedDrag),
        ) {
            // Music page: just the picture, its controls are under it.
            PlayerSlot(viewModel, state, ui, interactive = p == 0f && !musicView, isFullscreen = false, music = musicView,
                onToggleFullscreen = { setFullscreen(true) }, onMinimize = { minimize() },
                onSettings = { showSettings = true }, actions = playerActions, modifier = Modifier.fillMaxSize())
        }
    }

    if (showSettings && details != null) {
        PlayerSettingsSheet(
            qualities = details.streams.qualities,
            preferredHeight = state.preferredHeight,
            currentQualityLabel = state.qualityLabel,
            speed = state.speed,
            loop = state.loop,
            onQuality = viewModel::setQuality,
            onSpeed = viewModel::setSpeed,
            onLoop = viewModel::setLoop,
            immersive = isLandscape,
            onDismiss = { showSettings = false },
        )
    }
}

/** The video surface plus loading / error / unplayable fallbacks. */
@Composable
private fun PlayerSlot(
    viewModel: WatchViewModel,
    state: WatchState,
    ui: PlayerUiState,
    interactive: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
    actions: PlayerActions? = null,
    music: Boolean = false,
) {
    val details = state.details
    when {
        // Music page loading: a shimmering cover instead of the black video box.
        details == null && music -> Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).shimmer())
        details == null -> Box(modifier.background(Color.Black)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = Color.White, strokeWidth = 3.dp)
            }
        }
        !details.isPlayable -> Box(modifier.background(Color.Black)) {
            AsyncImage(details.thumbnail, null, Modifier.fillMaxSize())
            if (interactive) {
                Text(
                    "Aucun flux lisible pour cette vidéo",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                )
            }
        }
        else -> VideoPlayer(
            player = viewModel.player,
            ui = ui,
            title = details.title,
            isFullscreen = isFullscreen,
            interactive = interactive,
            onToggleFullscreen = onToggleFullscreen,
            onMinimize = onMinimize,
            onSettings = onSettings,
            onRetry = viewModel::retry,
            modifier = modifier,
            actions = actions,
        )
    }
}

/** Swipe-up shelf of related videos while in fullscreen. */
@Composable
private fun FullscreenRecommendations(
    visible: Boolean,
    videos: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && videos.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(top = 32.dp, bottom = 56.dp),
        ) {
            Text(
                "Vidéos suggérées",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 32.dp, bottom = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(videos, key = { it.url }) { video ->
                    Column(Modifier.width(240.dp).clickable { onClick(video) }) {
                        Thumbnail(video, Modifier.fillMaxWidth(), RoundedCornerShape(8.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            video.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            video.channelName.orEmpty(),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Light status-bar icons over the black player, immersive mode in landscape. */
@Composable
private fun SystemBarsEffect(darkStatusBar: Boolean, immersive: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity, darkStatusBar, immersive) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val wasLight = controller.isAppearanceLightStatusBars
        if (darkStatusBar) controller.isAppearanceLightStatusBars = false
        fun hideBars() {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        if (immersive) hideBars()
        // A dialog / bottom sheet on top (e.g. the player settings) brings the bars back when
        // it takes focus: hide them again once the player window has focus back.
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus && immersive) hideBars() }
        val observer = window.decorView.viewTreeObserver
        observer.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focusListener)
            controller.isAppearanceLightStatusBars = wasLight
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
private fun SideCommentsPanel(
    comments: CommentsState,
    actions: CommentActions,
    creatorAvatar: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Commentaires", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            if (comments.countText != null) {
                Text(
                    "  ${comments.countText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fermer") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CommentsList(comments, actions, creatorAvatar)
    }
}
