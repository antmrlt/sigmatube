package app.sigmatube.ui.playlist

import app.sigmatube.ui.components.SkeletonFeed
import app.sigmatube.ui.components.Motion
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.PlaylistDetails
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.LoadingState
import app.sigmatube.ui.components.VideoRow
import app.sigmatube.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class PlaylistUiState(
    val details: PlaylistDetails? = null,
    val videos: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
)

class PlaylistViewModel(private val url: String, private val repository: YouTubeRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = PlaylistUiState()
        viewModelScope.launch {
            runCatching { repository.playlist(url) }
                .onSuccess { (details, page) ->
                    _state.value = PlaylistUiState(details = details, videos = page.items, nextPage = page.nextPage, isLoading = false)
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val r = runCatching { repository.playlistMore(url, page) }.getOrNull()
            _state.update {
                it.copy(
                    videos = (it.videos + r?.items.orEmpty()).distinctBy { v -> v.url },
                    nextPage = r?.nextPage,
                    isLoadingMore = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onPlay: (videos: List<VideoItem>, start: Int, shuffle: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.details?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        val details = state.details
        when {
            state.isLoading -> SkeletonFeed(modifier, rows = true)
            state.error != null || details == null ->
                ErrorState(state.error ?: IllegalStateException(), onRetry = viewModel::load, modifier = modifier)
            else -> {
                val listState = rememberLazyListState()
                LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)
                LazyColumn(modifier, state = listState) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(details.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                Format.meta(details.uploader, if (details.videoCount >= 0) "${details.videoCount} vidéos" else null),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.videos.isNotEmpty()) {
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                    app.sigmatube.ui.components.Pill(filled = true, onClick = { onPlay(state.videos, 0, false) }) {
                                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                        Text("Tout lire", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    app.sigmatube.ui.components.Pill(onClick = { onPlay(state.videos, 0, true) }) {
                                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp))
                                        Text("Aléatoire", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                    items(state.videos, key = { it.url }) { video -> VideoRow(video, onClick = { onPlay(state.videos, state.videos.indexOf(video), false) }, modifier = Modifier.animateItem(fadeInSpec = tween(Motion.FADE_MS), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(120))) }
                    if (state.isLoadingMore) item { ListFooterLoader() }
                }
            }
        }
    }
}
