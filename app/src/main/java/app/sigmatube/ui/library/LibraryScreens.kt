package app.sigmatube.ui.library

import app.sigmatube.ui.components.TabReselectEffect
import app.sigmatube.ui.components.SkeletonFeed
import app.sigmatube.ui.components.Motion
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sigmatube.data.LocalHistory
import app.sigmatube.data.LocalPlaylists
import app.sigmatube.ui.components.PlaylistNameDialog
import app.sigmatube.ui.playlist.LocalPlaylistTile
import app.sigmatube.ui.components.MessageState
import app.sigmatube.ui.components.VideoRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import app.sigmatube.ui.components.VideoShelfCard
import app.sigmatube.util.Format
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.VideoItem
import app.sigmatube.data.auth.AccountInfo
import app.sigmatube.data.auth.AccountManager
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.data.auth.SubscribedChannel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.async
import app.sigmatube.ui.components.Avatar
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.LoadingState
import app.sigmatube.ui.components.VideoCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

// region Subscriptions

data class SubscriptionsUiState(
    val channels: List<SubscribedChannel> = emptyList(),
    val shelfTitle: String? = null,
    val shelf: List<VideoItem> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
)

class SubscriptionsViewModel(private val innerTube: InnerTubeClient) : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionsUiState())
    val state: StateFlow<SubscriptionsUiState> = _state

    fun load(refreshing: Boolean = false) {
        _state.update { it.copy(isLoading = !refreshing && it.videos.isEmpty(), isRefreshing = refreshing, error = null) }
        viewModelScope.launch {
            val channels = async { runCatching { innerTube.subscribedChannels() }.getOrDefault(emptyList()) }
            runCatching { innerTube.subscriptionsWithShelf() }
                .onSuccess { r ->
                    _state.update {
                        SubscriptionsUiState(
                            channels = channels.await(),
                            shelfTitle = r.shelfTitle,
                            shelf = r.shelf,
                            videos = r.videos,
                            nextPage = r.nextPage,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, isRefreshing = false, error = e) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val r = runCatching { innerTube.subscriptionsFeed(page) }.getOrNull()
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

@Composable
fun SubscriptionsScreen(
    account: AccountManager,
    viewModel: SubscriptionsViewModel,
    onLogin: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    val cookies by account.cookies.collectAsStateWithLifecycle()
    if (cookies == null) {
        SignInPrompt(
            title = "Tes abonnements",
            message = "Connecte-toi pour voir les dernières vidéos des chaînes auxquelles tu es abonné.",
            onLogin = onLogin,
        )
        return
    }
    LaunchedEffect(cookies) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)
    TabReselectEffect(listState, onRefresh = { viewModel.load(refreshing = true) })

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.load(refreshing = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading -> SkeletonFeed()
            state.error != null && state.videos.isEmpty() -> ErrorState(state.error!!, onRetry = { viewModel.load() })
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                if (state.channels.isNotEmpty()) {
                    item(key = "channels") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.channels, key = { it.id }) { channel ->
                                ChannelBubble(channel, onClick = { onChannelClick(channel.url) })
                            }
                        }
                    }
                }
                if (state.shelf.isNotEmpty()) {
                    item(key = "shelf-title") { SectionTitle("Les plus pertinentes") }
                    item(key = "shelf") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            items(state.shelf, key = { it.url }) { video ->
                                VideoShelfCard(video, onClick = { onVideoClick(video) })
                            }
                        }
                    }
                    item(key = "latest-title") { SectionTitle("Les plus récentes") }
                } else {
                    item(key = "title") { SectionTitle("Abonnements") }
                }
                items(state.videos, key = { it.url }) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onChannelClick = video.channelUrl?.let { url -> { onChannelClick(url) } },
                        modifier = Modifier.animateItem(fadeInSpec = tween(Motion.FADE_MS), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(120)),
                    )
                }
                if (state.isLoadingMore) item { ListFooterLoader() }
            }
        }
    }
}

/** Avatar + name in the subscriptions row; a blue dot marks channels with new videos. */
@Composable
private fun ChannelBubble(channel: SubscribedChannel, onClick: () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Avatar(channel.avatar, 56.dp)
            if (channel.hasNewContent) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            channel.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

// endregion

// region "Vous"

@Composable
fun LibraryScreen(
    account: AccountManager,
    accountInfo: AccountInfo?,
    history: LocalHistory,
    playlists: LocalPlaylists,
    onLogin: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenLocalPlaylist: (String) -> Unit,
) {
    val cookies by account.cookies.collectAsStateWithLifecycle()
    val watched by history.watched.collectAsStateWithLifecycle()
    val mine by playlists.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val loggedIn = cookies != null
    val listState = rememberLazyListState()
    TabReselectEffect(listState)

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "profile") {
            if (loggedIn) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(accountInfo?.avatar, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(accountInfo?.name ?: "Compte YouTube", style = MaterialTheme.typography.headlineSmall)
                        accountInfo?.handle?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Column(Modifier.padding(16.dp)) {
                    Text("Profite de ton compte", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Connecte-toi pour retrouver ton fil personnalisé, tes abonnements et tes playlists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    ) { Text("Se connecter") }
                }
            }
        }

        item(key = "history-title") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHistory)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Historique", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (watched.isNotEmpty()) {
                    Text(
                        "Tout afficher",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        item(key = "history") {
            if (watched.isEmpty()) {
                Text(
                    "Les vidéos que tu regardes apparaîtront ici.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(watched.take(20), key = { it.url }) { video ->
                        VideoShelfCard(video, onClick = { onVideoClick(video) }, modifier = Modifier.width(170.dp))
                    }
                }
            }
        }

        item(key = "playlists-title") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "Nouvelle",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { creating = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        item(key = "playlists") {
            if (mine.isEmpty()) {
                Text(
                    "Crée une playlist, ou utilise « Enregistrer dans une playlist » dans le menu ⋮ d'une vidéo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(mine, key = { it.id }) { p -> LocalPlaylistTile(p, onClick = { onOpenLocalPlaylist(p.id) }) }
                }
            }
        }

        if (loggedIn) {
            item(key = "library-title") {
                Text(
                    "Bibliothèque",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
                )
            }
            item(key = "liked") { LibraryRow(Icons.Outlined.ThumbUp, "Vidéos J'aime") { onOpenPlaylist(LIKED) } }
            item(key = "later") { LibraryRow(Icons.Outlined.WatchLater, "À regarder plus tard") { onOpenPlaylist(WATCH_LATER) } }
        }
        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }

    if (creating) {
        PlaylistNameDialog("Nouvelle playlist", "Créer", onDismiss = { creating = false }) { name ->
            creating = false
            onOpenLocalPlaylist(playlists.create(name))
        }
    }
}

const val LIKED = "LL"
const val WATCH_LATER = "WL"
const val HISTORY = "history"

@Composable
private fun LibraryRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// endregion

// region Full lists (history, liked, watch later)

data class VideoListUiState(
    val videos: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
)

/** Account playlist (liked / watch later) loaded through InnerTube. */
class AccountPlaylistViewModel(private val id: String, private val innerTube: InnerTubeClient) : ViewModel() {
    private val _state = MutableStateFlow(VideoListUiState())
    val state: StateFlow<VideoListUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = VideoListUiState()
        viewModelScope.launch {
            runCatching { innerTube.accountPlaylist(id) }
                .onSuccess { r -> _state.value = VideoListUiState(videos = r.items, nextPage = r.nextPage, isLoading = false) }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e) } }
        }
    }

    /** Reloads in place (list stays on screen); [pull] shows the pull-to-refresh spinner. */
    fun refresh(pull: Boolean = false) {
        val s = _state.value
        if (s.isLoading || s.isRefreshing) return
        if (s.videos.isEmpty()) return load()
        if (pull) _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            runCatching { innerTube.accountPlaylist(id) }
                .onSuccess { r -> _state.value = VideoListUiState(videos = r.items, nextPage = r.nextPage, isLoading = false) }
                .onFailure { _state.update { it.copy(isRefreshing = false) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.isLoading || s.isLoadingMore || s.isRefreshing) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val r = runCatching { innerTube.accountPlaylist(id, page) }.getOrNull()
            _state.update {
                it.copy(videos = (it.videos + r?.items.orEmpty()).distinctBy { v -> v.url }, nextPage = r?.nextPage, isLoadingMore = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    kind: String,
    history: LocalHistory,
    innerTube: InnerTubeClient,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
) {
    val title = when (kind) {
        HISTORY -> "Historique"
        LIKED -> "Vidéos J'aime"
        else -> "À regarder plus tard"
    }
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    if (kind == HISTORY) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Effacer l'historique") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        if (kind == HISTORY) {
            val watched by history.watched.collectAsStateWithLifecycle()
            if (watched.isEmpty()) {
                MessageState("Aucune vidéo regardée", "Les vidéos que tu regardes apparaîtront ici.", modifier, icon = Icons.Outlined.History)
            } else {
                LazyColumn(modifier) { items(watched, key = { it.url }) { VideoRow(it, onClick = { onVideoClick(it) }) } }
            }
        } else {
            val vm: AccountPlaylistViewModel = viewModel(key = kind) { AccountPlaylistViewModel(kind, innerTube) }
            val state by vm.state.collectAsStateWithLifecycle()
            val listState = rememberLazyListState()
            LoadMoreEffect(listState, onLoadMore = vm::loadMore)
            when {
                state.isLoading -> SkeletonFeed(modifier, rows = true)
                state.error != null -> ErrorState(state.error!!, onRetry = vm::load, modifier = modifier)
                state.videos.isEmpty() -> MessageState("Cette liste est vide", null, modifier)
                else -> LazyColumn(modifier, state = listState) {
                    items(state.videos, key = { it.url }) { VideoRow(it, onClick = { onVideoClick(it) }, modifier = Modifier.animateItem(fadeInSpec = tween(Motion.FADE_MS), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(120))) }
                    if (state.isLoadingMore) item { ListFooterLoader() }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text("Effacer tout l'historique des vidéos regardées ?") },
            confirmButton = { TextButton(onClick = { history.clearWatched(); confirmClear = false }) { Text("Effacer") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

// endregion

@Composable
private fun SignInPrompt(title: String, message: String, onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            when {
                title.contains("abonnement", true) -> Icons.Outlined.Subscriptions
                title.contains("plus tard", true) -> Icons.Outlined.WatchLater
                else -> Icons.Outlined.AccountCircle
            },
            null,
            Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLogin,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth(0.7f),
        ) {
            Text("Se connecter")
        }
    }
}
