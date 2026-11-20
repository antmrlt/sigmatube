package app.sigmatube.ui.watch

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.sigmatube.data.auth.LikeStatus
import coil3.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.sigmatube.ui.theme.Accent
import app.sigmatube.util.Format
import kotlinx.coroutines.delay

/** Observable snapshot of the player, refreshed from listener callbacks + a light poll for position. */
@Stable
class PlayerUiState(private val player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var isBuffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
    var isEnded by mutableStateOf(player.playbackState == Player.STATE_ENDED)
    var error by mutableStateOf<PlaybackException?>(player.playerError)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)

    fun sync() {
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING
        isEnded = player.playbackState == Player.STATE_ENDED
        error = player.playerError
        position = player.currentPosition
        duration = player.duration.coerceAtLeast(0)
        buffered = player.bufferedPosition
    }
}

@Composable
fun rememberPlayerUiState(player: Player): PlayerUiState {
    val state = remember(player) { PlayerUiState(player) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = state.sync()
        }
        player.addListener(listener)
        state.sync()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player, state.isPlaying) {
        while (true) {
            state.sync()
            delay(if (state.isPlaying) 250 else 1000)
        }
    }
    return state
}

/** Double-tap / ±button seek step, from settings. */
val LocalSeekMs = androidx.compose.runtime.compositionLocalOf { 10_000L }

private fun Player.seekBy(ms: Long) = seekTo((currentPosition + ms).coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE))

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    player: Player,
    ui: PlayerUiState,
    title: String?,
    isFullscreen: Boolean,
    interactive: Boolean,
    onToggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    actions: PlayerActions? = null,
) {
    val seekMs = LocalSeekMs.current
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableLongStateOf(0L) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    // Double-tap feedback: -1 = rewind, +1 = forward, 0 = none.
    var seekFeedback by remember { mutableIntStateOf(0) }
    var seekFeedbackTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(controlsVisible, ui.isPlaying, interactionTick, scrubFraction) {
        if (controlsVisible && ui.isPlaying && scrubFraction == null) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFeedbackTick) {
        if (seekFeedback != 0) {
            delay(700)
            seekFeedback = 0
        }
    }

    BoxWithConstraints(modifier.background(Color.Black)) {
        val widthPx = constraints.maxWidth

        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // Minimized: just the picture, the mini-player bar handles interaction.
        if (!interactive) return@BoxWithConstraints

        // Gesture layer: single tap toggles controls, double tap on a side seeks ±10 s.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interactionTick++
                        },
                        onDoubleTap = { offset ->
                            val forward = offset.x > widthPx / 2f
                            if (forward) player.seekBy(seekMs) else player.seekBy(-seekMs)
                            seekFeedback = if (forward) 1 else -1
                            seekFeedbackTick++
                        },
                    )
                },
        )

        val error = ui.error
        if (error != null) {
            PlayerError(error, onRetry, Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }

        if (seekFeedback != 0) {
            SeekFeedback(
                forward = seekFeedback > 0,
                modifier = Modifier
                    .align(if (seekFeedback > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 48.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || !ui.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(
                player = player,
                ui = ui,
                title = title.takeIf { isFullscreen },
                isFullscreen = isFullscreen,
                scrubFraction = scrubFraction,
                onToggleFullscreen = onToggleFullscreen,
                onMinimize = onMinimize,
                onSettings = onSettings,
                actions = actions,
                onInteraction = { interactionTick++ },
            )
        }

        if (ui.isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
        }

        // Portrait: thin line always on the bottom edge (thumb only with controls).
        // Fullscreen: hidden together with the controls, like YouTube.
        val showBar = !isFullscreen || controlsVisible || !ui.isPlaying || scrubFraction != null
        if (showBar) SeekBar(
            ui = ui,
            showThumb = controlsVisible || !ui.isPlaying || scrubFraction != null,
            scrubFraction = scrubFraction,
            onScrub = {
                scrubFraction = it
                controlsVisible = true
            },
            onScrubEnd = {
                scrubFraction?.let { f -> player.seekTo((f * ui.duration).toLong()) }
                scrubFraction = null
                interactionTick++
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = if (isFullscreen) 24.dp else 0.dp)
                .padding(bottom = if (isFullscreen) 64.dp else 0.dp),
        )
    }
}

/** Everything the YouTube-style controls can trigger (null pieces simply hide their button). */
class PlayerActions(
    val channel: String?,
    val autoplay: Boolean,
    val onAutoplay: (Boolean) -> Unit,
    val captionsOn: Boolean,
    val onCaptions: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val hasNext: Boolean,
    val likeStatus: LikeStatus,
    val onLike: () -> Unit,
    val onDislike: () -> Unit,
    val onComments: () -> Unit,
    val onSave: () -> Unit,
    val onShare: () -> Unit,
    val nextThumbnail: String?,
    val onMoreVideos: () -> Unit,
)

@Composable
private fun Controls(
    player: Player,
    ui: PlayerUiState,
    title: String?,
    isFullscreen: Boolean,
    scrubFraction: Float?,
    actions: PlayerActions?,
    onToggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onSettings: () -> Unit,
    onInteraction: () -> Unit,
) {
    fun act(block: () -> Unit) { block(); onInteraction() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
        // Top bar: ⌄ (+ title/channel in fullscreen) … autoplay · CC · settings
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = if (isFullscreen) 16.dp else 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = if (isFullscreen) onToggleFullscreen else onMinimize) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Réduire", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                if (title != null) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    actions?.channel?.let {
                        Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
            if (actions != null) {
                AutoplaySwitch(actions.autoplay) { act { actions.onAutoplay(!actions.autoplay) } }
                IconButton(onClick = { act(actions.onCaptions) }) {
                    Icon(
                        if (actions.captionsOn) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaption,
                        "Sous-titres",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            IconButton(onClick = { act(onSettings) }) {
                Icon(Icons.Outlined.Settings, "Paramètres", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        // Center: previous · play/pause · next
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 88.dp else 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.Rounded.SkipPrevious, "Précédente", 48.dp) { act { actions?.onPrevious?.invoke() ?: player.seekTo(0) } }
            if (ui.isBuffering) {
                Box(Modifier.size(64.dp))
            } else {
                val (icon, label) = when {
                    ui.isEnded -> Icons.Rounded.Replay to "Revoir"
                    ui.isPlaying -> Icons.Rounded.Pause to "Pause"
                    else -> Icons.Rounded.PlayArrow to "Lecture"
                }
                RoundIcon(icon, label, 64.dp) {
                    act {
                        when {
                            ui.isEnded -> player.seekTo(0)
                            ui.isPlaying -> player.pause()
                            else -> player.play()
                        }
                    }
                }
            }
            RoundIcon(
                Icons.Rounded.SkipNext,
                "Suivante",
                48.dp,
                enabled = actions?.hasNext == true,
            ) { act { actions?.onNext?.invoke() } }
        }

        // Time pill (left) and fullscreen toggle (right), just above the seek bar.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = if (isFullscreen) 24.dp else 12.dp,
                    end = if (isFullscreen) 20.dp else 4.dp,
                    bottom = if (isFullscreen) 84.dp else 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = scrubFraction?.let { (it * ui.duration).toLong() } ?: ui.position
            Text(
                "${Format.position(shown)} / ${Format.position(ui.duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (isFullscreen) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    if (isFullscreen) "Quitter le plein écran" else "Plein écran",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Fullscreen only: action pill (like · dislike · comments · save · share · more) + "Autres vidéos".
        if (isFullscreen && actions != null) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarIcon(if (actions.likeStatus == LikeStatus.Like) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "J'aime") {
                        act(actions.onLike)
                    }
                    BarIcon(if (actions.likeStatus == LikeStatus.Dislike) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Je n'aime pas") {
                        act(actions.onDislike)
                    }
                    BarIcon(Icons.Outlined.Comment, "Commentaires") { act(actions.onComments) }
                    BarIcon(Icons.Outlined.BookmarkBorder, "Enregistrer") { act(actions.onSave) }
                    BarIcon(Icons.AutoMirrored.Outlined.Reply, "Partager", Modifier.graphicsLayer(scaleX = -1f)) { act(actions.onShare) }
                    BarIcon(Icons.Rounded.MoreHoriz, "Plus") { act(onSettings) }
                }
                Spacer(Modifier.weight(1f))
                if (actions.nextThumbnail != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { act(actions.onMoreVideos) }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Autres vidéos", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(12.dp))
                        AsyncImage(
                            actions.nextThumbnail,
                            null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 64.dp, height = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, iconModifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = Color.White, modifier = iconModifier.size(24.dp))
    }
}

/** YouTube's autoplay pill: grey track, white knob with ▶ (on) or ⏸ (off). */
@Composable
private fun AutoplaySwitch(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(width = 40.dp, height = 22.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(horizontal = 1.dp).size(22.dp).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (enabled) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                if (enabled) "Lecture automatique activée" else "Lecture automatique désactivée",
                tint = Color.Black,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SeekBar(
    ui: PlayerUiState,
    showThumb: Boolean,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .offset(y = 9.dp) // centre the 3dp line on the player's bottom edge, like YouTube
            .pointerInput(ui.duration) {
                if (ui.duration <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { onScrub((it.x / size.width).coerceIn(0f, 1f)) },
                    onHorizontalDrag = { change, _ -> onScrub((change.position.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = onScrubEnd,
                    onDragCancel = onScrubEnd,
                )
            }
            .pointerInput(ui.duration) {
                if (ui.duration <= 0) return@pointerInput
                detectTapGestures { onScrub((it.x / size.width).coerceIn(0f, 1f)); onScrubEnd() }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val duration = ui.duration.coerceAtLeast(1)
        val played = scrubFraction ?: (ui.position.toFloat() / duration).coerceIn(0f, 1f)
        val buffered = (ui.buffered.toFloat() / duration).coerceIn(0f, 1f)
        val barHeight = if (scrubFraction != null) 5.dp else 3.dp

        Box(Modifier.fillMaxWidth().height(barHeight).background(Color.White.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth(buffered).height(barHeight).background(Color.White.copy(alpha = 0.5f)))
        Box(Modifier.fillMaxWidth(played).height(barHeight).background(Accent))
        if (showThumb) {
            val thumb = if (scrubFraction != null) 16.dp else 12.dp
            val x = with(density) { (constraints.maxWidth * played).toDp() } - thumb / 2
            Box(Modifier.offset(x = x).size(thumb).background(Accent, CircleShape))
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, size: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick, interactionSource = remember { MutableInteractionSource() }, indication = null),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
private fun SeekFeedback(forward: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind, null, tint = Color.White)
        Text("${LocalSeekMs.current / 1000} s", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayerError(error: PlaybackException, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Lecture impossible", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            error.errorCodeName,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        androidx.compose.material3.Button(
            onClick = onRetry,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) { Text("Réessayer") }
    }
}
