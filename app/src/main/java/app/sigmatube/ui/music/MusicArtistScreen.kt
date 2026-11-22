package app.sigmatube.ui.music

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.VideoItem
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.data.auth.MusicArtistPage
import app.sigmatube.data.auth.MusicCard
import app.sigmatube.data.withoutHidden
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.Pill
import app.sigmatube.ui.components.shimmer
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MusicArtistUiState(
    val page: MusicArtistPage? = null,
    val isLoading: Boolean = true,
    val error: Throwable? = null,
)

class MusicArtistViewModel(private val channelId: String, private val innerTube: InnerTubeClient) : ViewModel() {
    private val _state = MutableStateFlow(MusicArtistUiState())
    val state: StateFlow<MusicArtistUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = MusicArtistUiState()
        viewModelScope.launch {
            runCatching { innerTube.musicArtist(channelId) }
                .onSuccess { _state.value = MusicArtistUiState(page = it, isLoading = false) }
                .onFailure { e -> _state.value = MusicArtistUiState(isLoading = false, error = e) }
        }
    }
}

/** Callbacks shared by the music artist page and the music search. */
class MusicNavigation(
    val play: (videos: List<VideoItem>, start: Int, shuffle: Boolean) -> Unit,
    val album: (albumId: String) -> Unit,
    val artist: (channelId: String) -> Unit,
    val playlist: (playlistId: String) -> Unit,
)

/** Opens what a YouTube Music tile points to. */
fun MusicNavigation.open(card: MusicCard, siblings: List<MusicCard>, artist: String?) {
    when {
        card.albumId != null -> album(card.albumId)
        card.videoId != null -> {
            val videos = siblings.mapNotNull { it.toVideoItem(artist) }
            play(videos, videos.indexOfFirst { it.url.endsWith(card.videoId) }.coerceAtLeast(0), false)
        }
        card.artistId != null -> artist(card.artistId)
        card.playlistId != null -> playlist(card.playlistId)
    }
}

/** Artist page in the music style (opened from the Musique tab / music player only). */
@Composable
fun MusicArtistScreen(
    viewModel: MusicArtistViewModel,
    nav: MusicNavigation,
    onBack: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page = state.page
    val songs = page?.songs.orEmpty().withoutHidden(LocalHiddenChannels.current)
    var showAllSongs by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                if (page != null) {
                    AsyncImage(page.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else {
                    Box(Modifier.matchParentSize().shimmer())
                }
                // Fades into the page so the name reads on any photo.
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.35f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
                    if (page != null) {
                        Text(
                            page.name,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        page.subscribers?.let { raw ->
                            // YouTube Music gives a bare count ("7.22M"): say what it counts.
                            val it = if (Regex("""^[0-9.,\s]+[KMBkmd]?$""").matches(raw.trim())) "$raw abonnés" else raw
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Box(Modifier.size(220.dp, 36.dp).clip(RoundedCornerShape(10.dp)).shimmer())
                    }
                }
            }
        }
        item(key = "actions") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(filled = true, onClick = { if (songs.isNotEmpty()) nav.play(songs, 0, true) }) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Text("Aléatoire", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
                Pill(onClick = { if (songs.isNotEmpty()) nav.play(songs, 0, false) }) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Text("Tout lire", style = MaterialTheme.typography.labelLarge)
                }
                Pill(onClick = onOpenChannel) {
                    Icon(Icons.Outlined.OndemandVideo, null, Modifier.size(20.dp))
                    Text("Chaîne YouTube", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        when {
            state.isLoading -> {
                item(key = "songs-title") { MusicSectionTitle("Titres populaires") }
                items(5) { SongRowSkeleton() }
            }
            state.error != null || page == null -> item(key = "error") {
                ErrorState(state.error ?: IllegalStateException(), onRetry = viewModel::load, modifier = Modifier.height(320.dp))
            }
            else -> {
                if (songs.isNotEmpty()) {
                    item(key = "songs-title") { MusicSectionTitle("Titres populaires") }
                    val shown = if (showAllSongs) songs else songs.take(5)
                    itemsIndexed(shown, key = { _, v -> "s:${v.url}" }) { i, v ->
                        SongRow(v, onClick = { nav.play(songs, i, false) }, rank = i + 1)
                    }
                    if (songs.size > 5 && !showAllSongs) {
                        item(key = "more") {
                            Text(
                                "Afficher plus",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .padding(start = 16.dp, top = 4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { showAllSongs = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                page.shelves.forEach { shelf ->
                    item(key = "shelf-${shelf.title}") {
                        MusicSectionTitle(shelf.title)
                        MusicCardsRow(shelf.cards) { card -> nav.open(card, shelf.cards, page.name) }
                    }
                }
            }
        }
    }
}

