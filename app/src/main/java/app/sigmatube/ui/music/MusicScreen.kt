package app.sigmatube.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.LocalHistory
import app.sigmatube.data.LocalPlaylists
import app.sigmatube.data.MUSIC_GENRES
import app.sigmatube.data.MUSIC_MOODS
import app.sigmatube.data.MusicGenre
import app.sigmatube.data.MusicPreferences
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.data.withoutHidden
import app.sigmatube.data.looksLikeMusic
import app.sigmatube.ui.components.Avatar
import app.sigmatube.ui.components.LocalVideoMenu
import app.sigmatube.ui.components.PlaylistNameDialog
import app.sigmatube.ui.components.VideoMenuButton
import app.sigmatube.ui.components.VideoShelfCard
import app.sigmatube.ui.components.bouncyClickable
import app.sigmatube.ui.components.hqThumbnailUrl
import app.sigmatube.ui.components.pressScale
import app.sigmatube.ui.components.shimmer
import app.sigmatube.ui.playlist.LocalPlaylistTile
import app.sigmatube.ui.theme.Accent
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** One horizontal row of the page. */
data class Shelf(val items: List<VideoItem> = emptyList(), val loading: Boolean = true)

data class MusicUiState(
    val charts: Shelf = Shelf(),
    val hits: Shelf = Shelf(),
    val genres: Map<String, Shelf> = emptyMap(),
    /** "Parce que tu as écouté…": the seed video and its suggestions. */
    val because: Pair<VideoItem, Shelf>? = null,
    val mood: String? = null,
    val moodShelf: Shelf = Shelf(loading = false),
    val isRefreshing: Boolean = false,
)

class MusicViewModel(
    private val innerTube: InnerTubeClient,
    private val repository: YouTubeRepository,
    private val prefs: MusicPreferences,
    private val history: LocalHistory,
) : ViewModel() {
    private val _state = MutableStateFlow(MusicUiState())
    val state: StateFlow<MusicUiState> = _state
    private var moodJob: Job? = null

    init {
        load()
        // New styles picked: their rows load.
        viewModelScope.launch { prefs.genres.drop(1).distinctUntilChanged().collect { loadGenres(it) } }
        // A new song listened to in the music player: "Parce que tu as écouté…" follows it.
        viewModelScope.launch {
            history.music.map { it.firstOrNull()?.url }.distinctUntilChanged().drop(1).collect { loadBecause() }
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val charts = runCatching { innerTube.musicFeed() }.getOrDefault(emptyList())
            _state.update { it.copy(charts = Shelf(charts, loading = false), isRefreshing = false) }
        }
        viewModelScope.launch {
            val query = if (Locale.getDefault().language == "fr") "clip officiel" else "official music video"
            val hits = runCatching { innerTube.mostViewedThisMonth(query) }.getOrDefault(emptyList())
            _state.update { it.copy(hits = Shelf(hits.filter(::isSong), loading = false)) }
        }
        loadBecause()
        loadGenres(prefs.genres.value)
        _state.value.mood?.let(::loadMood)
    }

    private fun loadBecause() {
        val seed = history.music.value.firstOrNull() ?: return _state.update { it.copy(because = null) }
        _state.update { it.copy(because = seed to Shelf()) }
        viewModelScope.launch {
            val related = runCatching { repository.video(seed.url).related }.getOrDefault(emptyList()).filter { it.looksLikeMusic() }
            _state.update { it.copy(because = seed to Shelf(related, loading = false)) }
        }
    }

    private fun loadGenres(ids: Set<String>) {
        val genres = MUSIC_GENRES.filter { it.id in ids }
        _state.update { s -> s.copy(genres = genres.associate { g -> g.id to (s.genres[g.id]?.copy(loading = true) ?: Shelf()) }) }
        genres.forEach { g ->
            viewModelScope.launch {
                val items = runCatching { repository.search(g.query).items }.getOrDefault(emptyList()).filter(::isSong)
                _state.update { it.copy(genres = it.genres + (g.id to Shelf(items, loading = false))) }
            }
        }
    }

    /** Picks a mood (or un-picks it when tapped again). */
    fun selectMood(id: String) {
        if (_state.value.mood == id) {
            moodJob?.cancel()
            _state.update { it.copy(mood = null, moodShelf = Shelf(loading = false)) }
            return
        }
        _state.update { it.copy(mood = id) }
        loadMood(id)
    }

    private fun loadMood(id: String) {
        val mood = MUSIC_MOODS.firstOrNull { it.id == id } ?: return
        moodJob?.cancel()
        _state.update { it.copy(moodShelf = Shelf()) }
        moodJob = viewModelScope.launch {
            val found = runCatching { repository.search(mood.query).items }.getOrDefault(emptyList()).filterNot { it.isLive }
            // Songs first; long mixes only for focus / sleep, or when there's not enough else.
            val songs = found.filter(::isSong)
            val items = if (mood.id in LONG_MIX_MOODS || songs.size < 5) found else songs
            _state.update { it.copy(moodShelf = Shelf(items, loading = false)) }
        }
    }

    /** Songs rather than hour-long mixes or lives. */
    private fun isSong(v: VideoItem) = !v.isLive && (v.durationSeconds < 0 || v.durationSeconds in 60..900)
}

private val ShelfPadding = PaddingValues(horizontal = 16.dp)

/** Moods where hour-long mixes are what people want. */
private val LONG_MIX_MOODS = setOf("focus", "sleep")

/** "Musique" tab: moods, quick picks, recommendations by style, charts, artists, styles to explore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    prefs: MusicPreferences,
    history: LocalHistory,
    playlists: LocalPlaylists,
    queue: List<VideoItem>,
    onOpenQueue: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenGenre: (MusicGenre) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlay: (videos: List<VideoItem>, start: Int, shuffle: Boolean) -> Unit,
) {
    val onboarded by prefs.onboarded.collectAsStateWithLifecycle()
    if (!onboarded) {
        MusicOnboarding(initial = prefs.genres.collectAsStateWithLifecycle().value, onDone = prefs::setGenres)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hidden = LocalHiddenChannels.current
    val mine by playlists.playlists.collectAsStateWithLifecycle()
    val recent by history.music.collectAsStateWithLifecycle()
    val myGenres by prefs.genres.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    fun Shelf.visible() = items.withoutHidden(hidden)
    val charts = state.charts.visible()
    val hits = state.hits.visible()
    val genreShelves = MUSIC_GENRES.filter { it.id in myGenres }.map { it to (state.genres[it.id] ?: Shelf()) }
    val because = state.because?.let { (seed, shelf) -> seed to shelf.copy(items = shelf.visible()) }
    // Quick picks: a bit of every recommendation, interleaved.
    val quickPicks = remember(state, hidden, myGenres) {
        val sources = (listOfNotNull(because?.second?.items) + genreShelves.map { it.second.visible() } + listOf(charts)).filter { it.isNotEmpty() }
        val mixed = mutableListOf<VideoItem>()
        for (i in 0 until 6) sources.forEach { list -> list.getOrNull(i)?.let(mixed::add) }
        mixed.distinctBy { it.url }.take(20)
    }
    val quickLoading = quickPicks.isEmpty() && (state.charts.loading || genreShelves.any { it.second.loading })
    val artists = remember(state, hidden) {
        (charts + hits + genreShelves.flatMap { it.second.visible() } + because?.second?.items.orEmpty())
            .filter { it.channelUrl != null && it.channelAvatar != null && it.channelName != null }
            // A single artist, not "Shakira and 2 more".
            .filterNot { Regex("[0-9]+ (more|autres?)", RegexOption.IGNORE_CASE).containsMatchIn(it.channelName!!) }
            .distinctBy { it.channelUrl }
            .take(16)
    }

    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Mood picker, and what it found right under it.
            item(key = "moods") {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MUSIC_MOODS, key = { it.id }) { mood ->
                        MoodChip("${mood.emoji}  ${mood.label}", selected = state.mood == mood.id) { viewModel.selectMood(mood.id) }
                    }
                }
            }
            item(key = "mood-shelf") {
                val mood = MUSIC_MOODS.firstOrNull { it.id == state.mood }
                AnimatedVisibility(mood != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    if (mood != null) {
                        val items = state.moodShelf.visible()
                        Column {
                            ShelfHeader("Ambiance ${mood.label.lowercase()}", "Selon ton humeur", onPlayAll = items.takeIf { it.isNotEmpty() }?.let { { onPlay(it, 0, false) } })
                            LargeCardsRow(state.moodShelf.loading, items) { i -> onPlay(items, i, false) }
                        }
                    }
                }
            }

            if (queue.isNotEmpty()) item(key = "queue") { QueueCard(queue, onOpenQueue) }

            item(key = "quick") {
                ShelfHeader("Sélection rapide", "Un mélange de tes styles", onPlayAll = quickPicks.takeIf { it.isNotEmpty() }?.let { { onPlay(it, 0, true) } }, playAllLabel = "Aléatoire")
                QuickPicksGrid(quickLoading, quickPicks, ranked = false) { i -> onPlay(quickPicks, i, false) }
            }

            if (recent.isNotEmpty()) {
                item(key = "recent") {
                    val items = recent.withoutHidden(hidden)
                    ShelfHeader("Écoutés récemment", null, onPlayAll = { onPlay(items, 0, false) })
                    SquareCardsRow(false, items) { i -> onPlay(items, i, false) }
                }
            }

            item(key = "playlists") {
                ShelfHeader("Mes playlists", null, trailing = { OutlineAction("Nouvelle") { creating = true } })
                LazyRow(contentPadding = ShelfPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mine, key = { it.id }) { p -> LocalPlaylistTile(p, onClick = { onOpenPlaylist(p.id) }) }
                    item(key = "new") { NewPlaylistTile { creating = true } }
                }
            }

            if (because != null && (because.second.loading || because.second.items.isNotEmpty())) {
                item(key = "because") {
                    val items = because.second.items
                    ShelfHeader("Parce que tu as écouté", because.first.title, onPlayAll = items.takeIf { it.isNotEmpty() }?.let { { onPlay(it, 0, false) } })
                    LargeCardsRow(because.second.loading, items) { i -> onPlay(items, i, false) }
                }
            }

            item(key = "charts") {
                ShelfHeader("Tendances", "Les clips les plus regardés en ce moment", onPlayAll = charts.takeIf { it.isNotEmpty() }?.let { { onPlay(it, 0, false) } })
                QuickPicksGrid(state.charts.loading, charts, ranked = true) { i -> onPlay(charts, i, false) }
            }

            genreShelves.forEach { (genre, shelf) ->
                item(key = "genre-${genre.id}") {
                    val items = shelf.visible()
                    ShelfHeader(genre.label, "Pour toi", accent = genre.color, onPlayAll = items.takeIf { it.isNotEmpty() }?.let { { onPlay(it, 0, false) } })
                    SquareCardsRow(shelf.loading, items) { i -> onPlay(items, i, false) }
                }
            }

            item(key = "hits") {
                ShelfHeader("Hits du mois", "Les clips les plus vus ces 30 derniers jours", onPlayAll = hits.takeIf { it.isNotEmpty() }?.let { { onPlay(it, 0, false) } })
                LargeCardsRow(state.hits.loading, hits) { i -> onPlay(hits, i, false) }
            }

            if (artists.size >= 3) {
                item(key = "artists") {
                    ShelfHeader("Artistes", null)
                    LazyRow(contentPadding = ShelfPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(artists, key = { it.channelUrl!! }) { v -> ArtistBubble(v) { onChannelClick(v.channelUrl!!) } }
                    }
                }
            }

            item(key = "explore") {
                ShelfHeader("Explorer les styles", null)
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    contentPadding = ShelfPadding,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(138.dp),
                ) {
                    gridItems(MUSIC_GENRES, key = { it.id }) { g -> GenreTile(g) { onOpenGenre(g) } }
                }
            }

            item(key = "edit") {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = prefs::reopenOnboarding) {
                        Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Modifier mes styles")
                    }
                }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog("Nouvelle playlist", "Créer", onDismiss = { creating = false }) { name ->
            creating = false
            onOpenPlaylist(playlists.create(name))
        }
    }
}

// region Onboarding

/** First visit: which styles to recommend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicOnboarding(initial: Set<String>, onDone: (Set<String>) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("Quels styles de musique aimes-tu ?", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            Text(
                "Choisis-en quelques-uns : la page Musique te proposera des titres dans ces styles. Tu pourras les changer plus tard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MUSIC_GENRES.forEach { g ->
                    GenreChoice(g, selected = g.id in picked) {
                        picked = if (g.id in picked) picked - g.id else picked + g.id
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onDone(picked) }) { Text("Passer") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onDone(picked) },
                enabled = picked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            ) {
                Text(if (picked.isEmpty()) "Continuer" else "Continuer (${picked.size})")
            }
        }
    }
}

@Composable
private fun GenreChoice(genre: MusicGenre, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) genre.color else MaterialTheme.colorScheme.surfaceContainerHigh, tween(200), label = "genreBg")
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(genre.label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

// endregion

// region Shelves

@Composable
private fun ShelfHeader(
    title: String,
    subtitle: String?,
    accent: Color? = null,
    onPlayAll: (() -> Unit)? = null,
    playAllLabel: String = "Tout lire",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (accent != null) {
            Box(Modifier.size(width = 4.dp, height = 22.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (subtitle != null && title == "Parce que tu as écouté") {
                // Seed title as the headline, like YouTube Music.
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (onPlayAll != null) OutlineAction(playAllLabel, icon = if (playAllLabel == "Aléatoire") Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow, onClick = onPlayAll)
        trailing?.invoke()
    }
}

@Composable
private fun OutlineAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = if (icon != null) 8.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MoodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, tween(220), label = "moodBg")
    val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, tween(220), label = "moodFg")
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Wide 16:9 cards (mood, "parce que tu as écouté", hits). */
@Composable
private fun LargeCardsRow(loading: Boolean, items: List<VideoItem>, onClick: (Int) -> Unit) {
    LazyRow(contentPadding = ShelfPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading && items.isEmpty()) {
            items(3) {
                Column(Modifier.width(260.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
                }
            }
        } else {
            itemsIndexed(items, key = { _, v -> v.url }) { i, v -> VideoShelfCard(v, onClick = { onClick(i) }) }
        }
    }
}

/** Square "cover" cards (styles, recently played); long press for the video menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareCardsRow(loading: Boolean, items: List<VideoItem>, onClick: (Int) -> Unit) {
    val menu = LocalVideoMenu.current
    val haptics = LocalHapticFeedback.current
    LazyRow(contentPadding = ShelfPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading && items.isEmpty()) {
            items(4) {
                Column(Modifier.width(150.dp)) {
                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.85f).height(13.dp).clip(RoundedCornerShape(7.dp)).shimmer())
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                }
            }
        } else {
            itemsIndexed(items, key = { _, v -> v.url }) { i, v ->
                val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Column(
                    Modifier
                        .width(150.dp)
                        .pressScale(source, 0.95f)
                        .combinedClickable(
                            interactionSource = source,
                            indication = null,
                            onClick = { onClick(i) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu?.open(v)
                            },
                        ),
                ) {
                    Cover(v, Modifier.size(150.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        v.channelName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Columns of 4 compact rows, scrolled sideways (quick picks; charts with their rank). */
@Composable
private fun QuickPicksGrid(loading: Boolean, items: List<VideoItem>, ranked: Boolean, onClick: (Int) -> Unit) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(4),
        contentPadding = ShelfPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(64.dp * 4),
    ) {
        if (loading && items.isEmpty()) {
            items(8) {
                Row(Modifier.width(300.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).shimmer())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(0.85f).height(13.dp).clip(RoundedCornerShape(7.dp)).shimmer())
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                    }
                }
            }
        } else {
            gridItemsIndexed(items, key = { _, v -> (if (ranked) "r:" else "p:") + v.url }) { i, v ->
                Row(
                    Modifier
                        .width(300.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .bouncyClickable(pressedScale = 0.97f) { onClick(i) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(v, Modifier.size(52.dp), RoundedCornerShape(8.dp))
                    if (ranked) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp),
                        )
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            v.channelName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    VideoMenuButton(v)
                }
            }
        }
    }
}

/** Centre-cropped square thumbnail, like an album cover. */
@Composable
private fun Cover(video: VideoItem, modifier: Modifier, shape: RoundedCornerShape) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        // HD frame first; some videos don't have one, then the API's own thumbnail.
        val fallback = coil3.compose.rememberAsyncImagePainter(video.thumbnail)
        AsyncImage(
            model = remember(video.url) { hqThumbnailUrl(video.url) } ?: video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = fallback,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun ArtistBubble(video: VideoItem, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).bouncyClickable(pressedScale = 0.94f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(video.channelAvatar, 88.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            video.channelName.orEmpty().removeSuffix(" - Topic").removeSuffix("VEVO"),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GenreTile(genre: MusicGenre, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 150.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(genre.color, genre.color.copy(alpha = 0.65f))))
            .bouncyClickable(pressedScale = 0.95f, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(genre.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2)
    }
}

@Composable
private fun QueueCard(queue: List<VideoItem>, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.QueueMusic, null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("File d'attente · ${queue.size}", style = MaterialTheme.typography.titleSmall)
            Text(
                "À suivre : ${queue.first().title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NewPlaylistTile(onClick: () -> Unit) {
    Column(Modifier.width(168.dp).bouncyClickable(pressedScale = 0.95f, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Nouvelle playlist", style = MaterialTheme.typography.titleSmall)
    }
}

// endregion
