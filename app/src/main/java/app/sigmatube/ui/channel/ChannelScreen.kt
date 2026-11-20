package app.sigmatube.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.ChannelDetails
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.ui.components.Avatar
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.LoadingState
import app.sigmatube.ui.components.VideoCard
import app.sigmatube.util.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class ChannelUiState(
    val channel: ChannelDetails? = null,
    val videos: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
)

class ChannelViewModel(
    private val url: String,
    private val repository: YouTubeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { ChannelUiState(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                // Sequential on purpose: channel() caches the videos tab, saving a second fetch.
                val channel = repository.channel(url)
                channel to repository.channelVideos(url)
            }.onSuccess { (channel, videos) ->
                _state.update {
                    ChannelUiState(channel = channel, videos = videos.items, nextPage = videos.nextPage, isLoading = false)
                }
            }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.channelVideos(url, page) }
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            videos = (it.videos + r.items).distinctBy { v -> v.url },
                            nextPage = r.nextPage,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoadingMore = false, nextPage = null) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.channel?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val channel = state.channel
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            state.isLoading -> LoadingState(modifier)
            state.error != null || channel == null ->
                ErrorState(state.error ?: IllegalStateException(), onRetry = viewModel::load, modifier = modifier)
            else -> {
                val listState = rememberLazyListState()
                LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)
                LazyColumn(modifier, state = listState) {
                    item { Header(channel) }
                    items(state.videos, key = { it.url }) { video ->
                        VideoCard(video, onClick = { onVideoClick(video) }, onChannelClick = null)
                    }
                    if (state.isLoadingMore) item { ListFooterLoader() }
                }
            }
        }
    }
}

@Composable
private fun Header(channel: ChannelDetails) {
    Column {
        if (channel.banner != null) {
            AsyncImage(
                channel.banner,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(channel.avatar, 80.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Format.subscribers(channel.subscriberCount)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (channel.description.isNotBlank()) {
            Row(
                Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    channel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        // Single tab for now; more (Shorts, Live, Playlists) can slot in here.
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                "Vidéos",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            )
            Box(Modifier.width(64.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
    }
}
