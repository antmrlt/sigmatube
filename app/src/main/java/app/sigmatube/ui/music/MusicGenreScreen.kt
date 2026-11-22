package app.sigmatube.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.MusicGenre
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.withoutHidden
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.Pill
import app.sigmatube.ui.components.Thumbnail
import app.sigmatube.ui.components.VideoMenuButton
import app.sigmatube.ui.components.bouncyClickable
import app.sigmatube.ui.components.feedSkeleton
import app.sigmatube.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class GenreUiState(
    val videos: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
)

class MusicGenreViewModel(private val query: String, private val repository: YouTubeRepository) : ViewModel() {
    private val _state = MutableStateFlow(GenreUiState())
    val state: StateFlow<GenreUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = GenreUiState()
        viewModelScope.launch {
            runCatching { repository.search(query) }
                .onSuccess { r -> _state.value = GenreUiState(r.items.filterNot { it.isLive }, r.nextPage, isLoading = false) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val r = runCatching { repository.search(query, page) }.getOrNull()
            _state.update {
                it.copy(
                    videos = (it.videos + r?.items.orEmpty().filterNot { v -> v.isLive }).distinctBy { v -> v.url },
                    nextPage = r?.nextPage,
                    isLoadingMore = false,
                )
            }
        }
    }
}

/** One music style: coloured header, play all / shuffle, and its songs. */
@Composable
fun MusicGenreScreen(
    genre: MusicGenre,
    viewModel: MusicGenreViewModel,
    onBack: () -> Unit,
    onPlay: (videos: List<VideoItem>, start: Int, shuffle: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val videos = state.videos.withoutHidden(LocalHiddenChannels.current)
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(genre.color.copy(alpha = 0.55f), Color.Transparent)))
                    .statusBarsPadding(),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                Text(
                    genre.label,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(filled = true, onClick = { if (videos.isNotEmpty()) onPlay(videos, 0, false) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Text("Tout lire", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Pill(onClick = { if (videos.isNotEmpty()) onPlay(videos, 0, true) }) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp))
                        Text("Aléatoire", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        when {
            state.isLoading -> feedSkeleton(count = 7, rows = true)
            state.error != null && videos.isEmpty() -> item(key = "error") {
                ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.height(360.dp))
            }
            else -> {
                itemsIndexed(videos, key = { _, v -> v.url }) { i, v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bouncyClickable { onPlay(videos, i, false) }
                            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumbnail(v, Modifier.width(120.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                Format.meta(v.channelName, Format.views(v.viewCount) ?: v.viewsText),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        VideoMenuButton(v)
                    }
                }
                if (state.isLoadingMore) item(key = "more") { ListFooterLoader() }
            }
        }
        item(key = "end") { Box(Modifier.height(8.dp)) }
    }
}
