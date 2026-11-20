package app.sigmatube.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import app.sigmatube.data.AppSettings
import app.sigmatube.data.HiddenChannels
import app.sigmatube.data.LocalHistory
import app.sigmatube.data.VideoDetails
import app.sigmatube.data.WatchProgress
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.auth.AccountManager
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.data.auth.LikeStatus
import app.sigmatube.data.auth.SortChip
import app.sigmatube.data.auth.YtComment
import app.sigmatube.data.auth.YtCommentsPage
import app.sigmatube.data.withoutHidden
import app.sigmatube.player.PlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList

data class RepliesState(
    val items: List<YtComment> = emptyList(),
    val nextToken: String? = null,
    val loading: Boolean = false,
    val expanded: Boolean = false,
)

data class CommentsState(
    val comments: List<YtComment> = emptyList(),
    val countText: String? = null,
    val nextToken: String? = null,
    val sorts: List<SortChip> = emptyList(),
    val disabled: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val replies: Map<String, RepliesState> = emptyMap(),
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
    val hasPrevious: Boolean = false,
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
    private val hiddenChannels: HiddenChannels,
    private val watchProgress: WatchProgress,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchState(speed = settings.state.value.defaultSpeed))
    val state: StateFlow<WatchState> = _state
    val settingsState = settings.state

    val player get() = playerManager.player

    private var loadJob: Job? = null
    private var commentsJob: Job? = null

    /** Videos watched before the current one in this session ("previous" button). */
    private val backStack = ArrayDeque<String>()

    private val autoplayListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val s = _state.value
            if (playbackState == Player.STATE_ENDED) s.details?.url?.let { watchProgress.save(it, player.duration, player.duration) }
            if (playbackState == Player.STATE_ENDED && !s.loop && settings.state.value.autoplay &&
                playerManager.currentUrl == s.details?.url
            ) {
                next()
            }
        }
    }

    /** Remembers how far the current video got (thumbnail bar + resume). */
    private fun saveProgress() {
        val url = _state.value.details?.url ?: return
        if (playerManager.currentUrl != url) return
        watchProgress.save(url, player.currentPosition, player.duration)
    }

    init {
        player.addListener(autoplayListener)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) saveProgress() }
        })
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                if (player.isPlaying) saveProgress()
            }
        }
        // Signed in/out while a video is open: refresh its rating and comment actions.
        viewModelScope.launch {
            account.cookies.drop(1).collect { cookies ->
                val url = _state.value.url ?: return@collect
                if (cookies == null) _state.update { it.copy(likeStatus = LikeStatus.None, likeDelta = 0) }
                else loadLikeStatus(url)
                loadComments(url)
            }
        }
    }

    fun open(url: String) = open(url, pushHistory = true)

    private fun open(url: String, pushHistory: Boolean) {
        val current = _state.value.url
        if (url == current && _state.value.error == null) return
        if (pushHistory && current != null) backStack.addLast(current)
        saveProgress()
        loadJob?.cancel()
        commentsJob?.cancel()
        playerManager.stop(this)
        _state.update {
            WatchState(
                url = url,
                isLoading = true,
                speed = settings.state.value.defaultSpeed,
                loop = it.loop,
                hasPrevious = backStack.isNotEmpty(),
            )
        }
        load(url, startPositionMs = watchProgress.resumePosition(url))
        loadComments(url)
        loadLikeStatus(url)
    }

    /** Next = first suggested video (skipping hidden channels), like YouTube's autoplay. */
    fun next() {
        val related = _state.value.details?.related.orEmpty().withoutHidden(hiddenChannels.ids.value)
        related.firstOrNull { !it.isLive }?.let { open(it.url) }
    }

    /** Restart if we're past the first seconds, otherwise go back to the previous video. */
    fun previous() {
        if (player.currentPosition > 3_000 || backStack.isEmpty()) {
            player.seekTo(0)
            return
        }
        open(backStack.removeLast(), pushHistory = false)
    }

    fun setAutoplay(enabled: Boolean) = settings.update { it.copy(autoplay = enabled) }

    fun toggleCaptions() {
        val details = _state.value.details ?: return
        if (details.subtitles.isEmpty()) {
            _state.update { it.copy(message = "Aucun sous-titre disponible pour cette vidéo") }
            return
        }
        val enabled = !settings.state.value.captions
        settings.update { it.copy(captions = enabled) }
        playerManager.setCaptionsEnabled(enabled)
    }

    /** Returns false when the user must sign in first. */
    fun saveToWatchLater(): Boolean {
        if (!account.isLoggedIn) return false
        val id = _state.value.url?.let(::videoId) ?: return true
        viewModelScope.launch {
            runCatching { innerTube.addToWatchLater(id) }
                .onSuccess { _state.update { it.copy(message = "Ajoutée à « À regarder plus tard »") } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Action impossible") } }
        }
        return true
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
        saveProgress()
        loadJob?.cancel()
        commentsJob?.cancel()
        playerManager.stop(this)
        backStack.clear()
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
        playerManager.setCaptionsEnabled(settings.state.value.captions)
        player.setPlaybackSpeed(s.speed)
        player.repeatMode = if (s.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _state.update { it.copy(loop = loop) }
    }

    // region Comments

    private fun loadComments(url: String) {
        val id = videoId(url) ?: return
        commentsJob?.cancel()
        _state.update { it.copy(comments = CommentsState(isLoading = true)) }
        commentsJob = viewModelScope.launch {
            runCatching { innerTube.comments(id) }
                .recoverCatching { fallbackComments(url) }
                .onSuccess { page -> if (_state.value.url == url) _state.update { it.copy(comments = page.toState()) } }
                .onFailure { _state.update { it.copy(comments = CommentsState(isLoading = false)) } }
        }
    }

    /** NewPipe as a backup (no replies/likes), if InnerTube's format ever changes. */
    private suspend fun fallbackComments(url: String): YtCommentsPage {
        val page = repository.comments(url)
        return YtCommentsPage(
            comments = page.comments.map {
                YtComment(
                    id = it.id, author = it.author, authorAvatar = it.authorAvatar, authorChannelId = null,
                    isCreator = false, isVerified = false, text = it.text,
                    publishedText = it.uploadedText.orEmpty(), likeCountText = it.likeCount.takeIf { n -> n > 0 }?.toString(),
                    replyCountText = it.replyCount.takeIf { n -> n > 0 }?.toString(), repliesToken = null,
                    isHearted = it.isHearted, isPinned = it.isPinned, likeState = LikeStatus.None,
                    likeAction = null, unlikeAction = null, dislikeAction = null, undislikeAction = null,
                )
            },
            nextToken = null,
            sorts = emptyList(),
            countText = page.totalCount.takeIf { it > 0 }?.let { app.sigmatube.util.Format.compact(it.toLong()) },
            disabled = page.disabled,
        )
    }

    private fun YtCommentsPage.toState() = CommentsState(
        comments = comments,
        countText = countText?.let(::compactCount),
        nextToken = nextToken,
        sorts = sorts,
        disabled = disabled,
        isLoading = false,
    )

    /** "2,462,453 Comments" -> "2,5 M" (YouTube's header text isn't localised). */
    private fun compactCount(text: String): String =
        text.filter(Char::isDigit).toLongOrNull()?.let { app.sigmatube.util.Format.compact(it) } ?: text

    fun sortComments(chip: SortChip) {
        if (chip.selected) return
        val current = _state.value.comments
        _state.update {
            it.copy(comments = CommentsState(isLoading = true, countText = current.countText,
                sorts = current.sorts.map { s -> s.copy(selected = s.token == chip.token) }))
        }
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            runCatching { innerTube.commentsPage(chip.token) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(comments = page.toState().copy(
                            sorts = it.comments.sorts,
                            countText = page.countText?.let(::compactCount) ?: it.comments.countText,
                        ))
                    }
                }
                .onFailure { _state.update { it.copy(comments = it.comments.copy(isLoading = false)) } }
        }
    }

    fun loadMoreComments() {
        val c = _state.value.comments
        val token = c.nextToken ?: return
        if (c.isLoading || c.isLoadingMore) return
        _state.update { it.copy(comments = c.copy(isLoadingMore = true)) }
        commentsJob = viewModelScope.launch {
            val page = runCatching { innerTube.commentsPage(token) }.getOrNull()
            _state.update {
                val cur = it.comments
                it.copy(comments = cur.copy(
                    comments = (cur.comments + page?.comments.orEmpty()).distinctBy { x -> x.id },
                    nextToken = page?.nextToken,
                    isLoadingMore = false,
                ))
            }
        }
    }

    /** Expands (loading the first 10) or collapses a comment's replies. */
    fun toggleReplies(comment: YtComment) {
        val existing = _state.value.comments.replies[comment.id]
        if (existing?.expanded == true) {
            updateReplies(comment.id) { it.copy(expanded = false) }
            return
        }
        if (existing != null && existing.items.isNotEmpty()) {
            updateReplies(comment.id) { it.copy(expanded = true) }
            return
        }
        val token = comment.repliesToken ?: return
        loadReplies(comment.id, token, reset = true)
    }

    fun loadMoreReplies(commentId: String) {
        val r = _state.value.comments.replies[commentId] ?: return
        val token = r.nextToken ?: return
        if (r.loading) return
        loadReplies(commentId, token, reset = false)
    }

    private fun loadReplies(commentId: String, token: String, reset: Boolean) {
        updateReplies(commentId) { it.copy(loading = true, expanded = true) }
        viewModelScope.launch {
            val page = runCatching { innerTube.commentReplies(token) }.getOrNull()
            updateReplies(commentId) {
                it.copy(
                    items = ((if (reset) emptyList() else it.items) + page?.comments.orEmpty()).distinctBy { c -> c.id },
                    nextToken = page?.nextToken,
                    loading = false,
                )
            }
        }
    }

    private fun updateReplies(commentId: String, transform: (RepliesState) -> RepliesState) =
        _state.update {
            val c = it.comments
            it.copy(comments = c.copy(replies = c.replies + (commentId to transform(c.replies[commentId] ?: RepliesState()))))
        }

    /** Like / dislike a comment (or undo). Returns false when the user must sign in first. */
    fun rateComment(comment: YtComment, target: LikeStatus): Boolean {
        if (!account.isLoggedIn) return false
        val next = if (comment.likeState == target) LikeStatus.None else target
        val action = when {
            next == LikeStatus.Like -> comment.likeAction
            next == LikeStatus.Dislike -> comment.dislikeAction
            comment.likeState == LikeStatus.Like -> comment.unlikeAction
            else -> comment.undislikeAction
        }
        if (action == null) {
            _state.update { it.copy(message = "Recharge la vidéo pour noter ce commentaire") }
            return true
        }
        val updated = comment.copy(
            likeState = next,
            likeCountText = adjustCount(comment.likeCountText, likeCountChange(comment.likeState, next)),
        )
        replaceComment(updated)
        viewModelScope.launch {
            runCatching { innerTube.performCommentAction(action) }.onFailure { e ->
                replaceComment(comment)
                _state.update { it.copy(message = e.message ?: "Action impossible") }
            }
        }
        return true
    }

    /** "12" -> "13"; abbreviated counts ("1,2 k") are left as is, like YouTube. */
    private fun adjustCount(text: String?, delta: Long): String? {
        if (delta == 0L) return text
        val n = text?.trim()?.toLongOrNull() ?: return if (text == null && delta > 0) "1" else text
        return (n + delta).takeIf { it > 0 }?.toString()
    }

    private fun replaceComment(updated: YtComment) = _state.update {
        val c = it.comments
        it.copy(comments = c.copy(
            comments = c.comments.map { x -> if (x.id == updated.id) updated else x },
            replies = c.replies.mapValues { (_, r) -> r.copy(items = r.items.map { x -> if (x.id == updated.id) updated else x }) },
        ))
    }

    // endregion

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
        // Stored as text in the local history, so keep the relative form ("il y a 3 jours").
        uploadedText = app.sigmatube.util.Format.relative(uploadDate, uploadedText),
        isLive = isLive,
    )

    fun pause() {
        saveProgress()
        playerManager.pause(this)
    }

    override fun onCleared() {
        player.removeListener(autoplayListener)
        playerManager.stop(this)
    }
}
