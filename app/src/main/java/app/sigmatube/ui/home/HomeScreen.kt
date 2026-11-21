package app.sigmatube.ui.home

import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.animateColorAsState
import app.sigmatube.ui.components.pressScale
import app.sigmatube.ui.components.feedSkeleton
import app.sigmatube.ui.components.Motion
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import app.sigmatube.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.FeedCategory
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.VideoItem
import app.sigmatube.data.withoutHidden
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.auth.AccountManager
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.LoadingState
import app.sigmatube.ui.components.VideoCard
import app.sigmatube.ui.theme.Accent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import app.sigmatube.data.AppSettings
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

/** null = "Tout". */
data class HomeUiState(
    val categories: List<FeedCategory> = emptyList(),
    val selected: String? = null,
    val videos: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
)

class HomeViewModel(
    private val repository: YouTubeRepository,
    private val innerTube: InnerTubeClient,
    private val account: AccountManager,
    settings: AppSettings,
) : ViewModel() {

    val hideLives = settings.state.map { it.hideLives }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.state.value.hideLives)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state
    private var job: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repository.categories() }.onSuccess { c -> _state.update { it.copy(categories = c) } }
        }
        load()
        // Sign in / out: refresh the "Tout" feed.
        viewModelScope.launch {
            account.cookies.drop(1).distinctUntilChanged().collect { if (_state.value.selected == null) load() }
        }
    }

    fun select(category: String?) {
        if (category == _state.value.selected && _state.value.error == null) return
        _state.update { it.copy(selected = category) }
        load()
    }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        val selected = _state.value.selected
        job?.cancel()
        _state.update {
            it.copy(isLoading = !refreshing, isRefreshing = refreshing, error = null, nextPage = null,
                videos = if (refreshing) it.videos else emptyList())
        }
        job = viewModelScope.launch {
            runCatching {
                when {
                    selected != null -> repository.category(selected) to null
                    account.isLoggedIn -> innerTube.homeFeed().let { it.items to it.nextPage }
                    else -> repository.discover() to null
                }
            }.onSuccess { (videos, next) ->
                _state.update { it.copy(videos = videos, nextPage = next, isLoading = false, isRefreshing = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.isLoading || s.isLoadingMore || s.selected != null) return
        _state.update { it.copy(isLoadingMore = true) }
        job = viewModelScope.launch {
            val result = runCatching { innerTube.homeFeed(page) }.getOrNull()
            _state.update {
                it.copy(
                    videos = (it.videos + result?.items.orEmpty()).distinctBy { v -> v.url },
                    nextPage = result?.nextPage,
                    isLoadingMore = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hiddenIds = LocalHiddenChannels.current
    val hideLives = viewModel.hideLives.collectAsStateWithLifecycle().value

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val listState = rememberLazyListState()
        LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = "chips") {
                CategoryChips(state.categories, state.selected, viewModel::select)
            }
            when {
                state.isLoading -> feedSkeleton()
                state.error != null && state.videos.isEmpty() -> item(key = "error") {
                    ErrorState(state.error!!, onRetry = { viewModel.load() }, modifier = Modifier.height(400.dp))
                }
                else -> {
                    val visible = state.videos.withoutHidden(hiddenIds).let { v -> if (hideLives) v.filterNot { it.isLive } else v }
                    items(visible, key = { it.url }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onChannelClick = video.channelUrl?.let { url -> { onChannelClick(url) } },
                            modifier = Modifier.animateItem(fadeInSpec = tween(Motion.FADE_MS), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(120)),
                        )
                    }
                    if (state.isLoadingMore) item(key = "more") { ListFooterLoader() }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<FeedCategory>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Chip("Tout", selected == null) { onSelect(null) } }
        items(categories, key = { it.id }) { c -> Chip(c.label, selected == c.id) { onSelect(c.id) } }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Colours cross-fade and the chip sinks under the finger.
    val bg by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, tween(220), label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, tween(220), label = "chipFg")
    val source = remember { MutableInteractionSource() }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

