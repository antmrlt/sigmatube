package app.sigmatube.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.Comment
import app.sigmatube.data.VideoDetails
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.AppSettings
import app.sigmatube.data.LocalHistory
import app.sigmatube.data.VideoItem
import app.sigmatube.data.auth.AccountManager
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.data.auth.LikeStatus
import org.schabi.newpipe.extractor.ServiceList
import app.sigmatube.player.PlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class CommentsState(
    val comments: List<Comment> = emptyList(),
    val totalCount: Int = -1,
    val nextPage: Page? = null,
    val disabled: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
)

data class WatchState(
    /** Null when nothing is open (overlay hidden). */
    val url: String? = null,
    val details: VideoDetails? = null,
    val isLoading: Boolean = false,
    val error: Throwable? = null,
    val comments: CommentsState = CommentsState(),
    /** User-picked height, null = Auto. */
    val preferredHeight: Int? = null,
    val qualityLabel: String? = null,
    val speed: Float = 1f,
    val loop: Boolean = false,
    val likeStatus: LikeStatus = LikeStatus.None,
    /** Local adjustment to the public like count after the user (un)likes. */
    val likeDelta: Long = 0,
    /** One-shot message to show (toast), consumed by the UI. */
    val message: String? = null,
)

/**
 * Activity-scoped: the video being watched survives navigation, which is what makes the
 * mini-player possible. Opening another video replaces the current one (like YouTube).
 */
class WatchViewModel(
    private val repository: YouTubeRepository,
    private val playerManager: PlayerManager,
    private val innerTube: InnerTubeClient,
    private val account: AccountManager,
    private val settings: AppSettings,
    private val history: LocalHistory,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchState(speed = settings.state.value.defaultSpeed))
    val state: StateFlow<WatchState> = _state

    val player get() = playerManager.player

    private var loadJob: Job? = null
    private var commentsJob: Job? = null

    init {
        // Signed in/out while a video is open: refresh its rating.
        viewModelScope.launch {
            account.cookies.drop(1).collect { cookies ->
                val url = _state.value.url ?: return@collect
                if (cookies == null) _state.update { it.copy(likeStatus = LikeStatus.None, likeDelta = 0) }
                else loadLikeStatus(url)
            }
        }
    }

    fun open(url: String) {
        if (url == _state.value.url && _state.value.error == null) return
        loadJob?.cancel()
        commentsJob?.cancel()
        playerManager.stop(this)
        _state.update {
            WatchState(url = url, isLoading = true, speed = settings.state.value.defaultSpeed, loop = it.loop)
        }
        load(url, startPositionMs = 0)
        loadComments(url)
        loadLikeStatus(url)
    }

    private fun videoId(url: String): String? =
        runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }.getOrNull()

    private fun loadLikeStatus(url: String) {
        if (!account.isLoggedIn) return
        val id = videoId(url) ?: return
        viewModelScope.launch {
            runCatching { innerTube.likeStatus(id) }.onSuccess { status ->
                if (_state.value.url == url) _state.update { it.copy(likeStatus = status, likeDelta = 0) }
            }
        }
    }

    /** Returns false when the user must sign in first. */
    fun toggleRating(target: LikeStatus): Boolean {
        if (!account.isLoggedIn) return false
        val s = _state.value
        val id = s.url?.let(::videoId) ?: return true
        val previous = s.likeStatus to s.likeDelta
        val next = if (s.likeStatus == target) LikeStatus.None else target
        val delta = s.likeDelta + likeCountChange(s.likeStatus, next)
        _state.update { it.copy(likeStatus = next, likeDelta = delta) }
        viewModelScope.launch {
            runCatching { innerTube.rate(id, next) }.onFailure { e ->
                _state.update {
                    it.copy(likeStatus = previous.first, likeDelta = previous.second, message = e.message ?: "Action impossible")
                }
            }
        }
        return true
    }

    private fun likeCountChange(from: LikeStatus, to: LikeStatus): Long =
        (if (to == LikeStatus.Like) 1L else 0L) - (if (from == LikeStatus.Like) 1L else 0L)

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun close() {
        loadJob?.cancel()
        commentsJob?.cancel()
        playerManager.stop(this)
        _state.update { WatchState(speed = it.speed, loop = it.loop) }
    }

    /** Re-fetches stream info (URLs expire) and resumes where we were. */
    fun retry() {
        val url = _state.value.url ?: return
        _state.update { it.copy(isLoading = it.details == null, error = null) }
        load(url, startPositionMs = player.currentPosition)
    }

    private fun load(url: String, startPositionMs: Long) {
        loadJob = viewModelScope.launch {
            runCatching { repository.video(url) }
                .onSuccess { details ->
                    history.addWatched(details.toItem())
                    _state.update { it.copy(details = details, isLoading = false, error = null) }
                    startPlayback(details, startPositionMs)
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e) } }
        }
    }

    private fun startPlayback(details: VideoDetails, positionMs: Long) {
        val s = _state.value
        playerManager.play(this, details, positionMs, s.preferredHeight, settings.autoMaxHeight())
        player.setPlaybackSpeed(s.speed)
        player.repeatMode = if (s.loop) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
        _state.update { it.copy(qualityLabel = playerManager.currentQualityLabel) }
    }

    fun setQuality(height: Int?) {
        val details = _state.value.details ?: return
        _state.update { it.copy(preferredHeight = height) }
        val wasPlaying = player.playWhenReady
        startPlayback(details, player.currentPosition)
        player.playWhenReady = wasPlaying
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setLoop(loop: Boolean) {
        player.repeatMode = if (loop) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
        _state.update { it.copy(loop = loop) }
    }

    private fun loadComments(url: String) {
        commentsJob = viewModelScope.launch {
            runCatching { repository.comments(url) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            comments = CommentsState(
                                comments = page.comments,
                                totalCount = page.totalCount,
                                nextPage = page.nextPage,
                                disabled = page.disabled,
                                isLoading = false,
                            ),
                        )
                    }
                }
                .onFailure { _state.update { it.copy(comments = CommentsState(isLoading = false)) } }
        }
    }

    fun loadMoreComments() {
        val url = _state.value.url ?: return
        val c = _state.value.comments
        val page = c.nextPage ?: return
        if (c.isLoading || c.isLoadingMore) return
        _state.update { it.copy(comments = c.copy(isLoadingMore = true)) }
        commentsJob = viewModelScope.launch {
            val result = runCatching { repository.comments(url, page) }.getOrNull()
            _state.update {
                val current = it.comments
                it.copy(
                    comments = current.copy(
                        comments = (current.comments + result?.comments.orEmpty()).distinctBy { x -> x.id },
                        nextPage = result?.nextPage,
                        isLoadingMore = false,
                    ),
                )
            }
        }
    }

    private fun VideoDetails.toItem() = VideoItem(
        url = url,
        title = title,
        thumbnail = thumbnail,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatar = channelAvatar,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        uploadDate = uploadDate,
        uploadedText = uploadedText,
        isLive = isLive,
    )

    fun pause() = playerManager.pause(this)

    override fun onCleared() {
        playerManager.stop(this)
    }
}
