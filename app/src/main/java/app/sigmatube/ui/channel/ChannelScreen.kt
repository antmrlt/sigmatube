package app.sigmatube.ui.channel

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.ChannelDetails
import app.sigmatube.data.HiddenChannels
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.auth.AccountManager
import app.sigmatube.data.auth.ChannelPost
import app.sigmatube.data.auth.ChannelShelf
import app.sigmatube.data.auth.ChannelTab
import app.sigmatube.data.auth.ChannelTabKind
import app.sigmatube.data.auth.InnerTubeClient
import app.sigmatube.data.auth.PlaylistItem
import app.sigmatube.data.auth.SortChip
import app.sigmatube.ui.components.Avatar
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.LoadingState
import app.sigmatube.ui.components.PlaylistCard
import app.sigmatube.ui.components.VideoCard
import app.sigmatube.ui.components.VideoShelfCard
import app.sigmatube.ui.components.userMessage
import app.sigmatube.util.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class TabContent(
    val loading: Boolean = true,
    val error: Throwable? = null,
    val videos: List<VideoItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val posts: List<ChannelPost> = emptyList(),
    val shelves: List<ChannelShelf> = emptyList(),
    val sorts: List<SortChip> = emptyList(),
    val nextPage: Page? = null,
    val loadingMore: Boolean = false,
)

data class ChannelUiState(
    val channel: ChannelDetails? = null,
    val isLoading: Boolean = true,
    val error: Throwable? = null,
    val tabs: List<ChannelTab> = emptyList(),
    val selected: ChannelTabKind? = null,
    val content: Map<ChannelTabKind, TabContent> = emptyMap(),
    /** null = unknown (signed out or not available). */
    val subscribed: Boolean? = null,
    val message: String? = null,
)

class ChannelViewModel(
    private val url: String,
    private val repository: YouTubeRepository,
    private val innerTube: InnerTubeClient,
    private val account: AccountManager,
    private val hiddenChannels: HiddenChannels,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelUiState())
    val state: StateFlow<ChannelUiState> = _state
    val hiddenIds = hiddenChannels.ids

    init {
        load()
    }

    fun load() {
        _state.value = ChannelUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                val channel = repository.channel(url)
                val page = runCatching { innerTube.channelPage(channel.id) }.getOrNull()
                channel to page
            }.onSuccess { (channel, page) ->
                // Without InnerTube tabs we still offer the videos list through NewPipe.
                val tabs = page?.tabs?.takeIf { it.isNotEmpty() } ?: listOf(ChannelTab(ChannelTabKind.Videos, ""))
                val first = tabs.firstOrNull { it.kind == ChannelTabKind.Home }?.kind ?: tabs.first().kind
                _state.update {
                    it.copy(channel = channel, isLoading = false, tabs = tabs, selected = first, subscribed = page?.subscribed)
                }
                loadTab(first)
            }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e) } }
        }
    }

    fun select(kind: ChannelTabKind) {
        _state.update { it.copy(selected = kind) }
        val existing = _state.value.content[kind]
        if (existing == null || existing.error != null) loadTab(kind)
    }

    private fun tab(kind: ChannelTabKind) = _state.value.tabs.firstOrNull { it.kind == kind }

    private fun updateContent(kind: ChannelTabKind, transform: (TabContent) -> TabContent) =
        _state.update { it.copy(content = it.content + (kind to transform(it.content[kind] ?: TabContent()))) }

    private fun loadTab(kind: ChannelTabKind) {
        val channel = _state.value.channel ?: return
        val tab = tab(kind) ?: return
        updateContent(kind) { TabContent(loading = true) }
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    ChannelTabKind.Home -> TabContent(loading = false, shelves = innerTube.channelHome(channel.id, tab.params))
                    ChannelTabKind.Videos -> if (tab.params.isEmpty()) {
                        val r = repository.channelVideos(url)
                        TabContent(loading = false, videos = r.items, nextPage = r.nextPage)
                    } else {
                        val r = innerTube.channelVideos(channel.id, tab.params)
                        TabContent(loading = false, videos = r.videos, sorts = r.sorts, nextPage = r.nextPage)
                    }
                    ChannelTabKind.Live -> innerTube.channelVideosSimple(channel.id, tab.params)
                        .let { TabContent(loading = false, videos = it.items, nextPage = it.nextPage) }
                    ChannelTabKind.Playlists -> innerTube.channelPlaylists(channel.id, tab.params)
                        .let { TabContent(loading = false, playlists = it.items, nextPage = it.nextPage) }
                    ChannelTabKind.Posts -> innerTube.channelPosts(channel.id, tab.params)
                        .let { TabContent(loading = false, posts = it.items, nextPage = it.nextPage) }
                }
            }.onSuccess { c -> updateContent(kind) { c } }
                .onFailure { e -> updateContent(kind) { TabContent(loading = false, error = e) } }
        }
    }

    fun sort(chip: SortChip) {
        val channel = _state.value.channel ?: return
        val tab = tab(ChannelTabKind.Videos) ?: return
        val current = _state.value.content[ChannelTabKind.Videos] ?: return
        if (chip.selected) return
        updateContent(ChannelTabKind.Videos) {
            it.copy(loading = true, videos = emptyList(), sorts = current.sorts.map { s -> s.copy(selected = s.token == chip.token) })
        }
        viewModelScope.launch {
            runCatching { innerTube.channelVideos(channel.id, tab.params, chip.token) }
                // Keep our chip list (the re-sorted response may omit it), with the new selection.
                .onSuccess { r -> updateContent(ChannelTabKind.Videos) { it.copy(loading = false, videos = r.videos, nextPage = r.nextPage) } }
                .onFailure { e -> updateContent(ChannelTabKind.Videos) { it.copy(loading = false, error = e) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val kind = s.selected ?: return
        val channel = s.channel ?: return
        val tab = tab(kind) ?: return
        val c = s.content[kind] ?: return
        val page = c.nextPage ?: return
        if (c.loading || c.loadingMore) return
        updateContent(kind) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching {
                when (kind) {
                    ChannelTabKind.Videos -> if (tab.params.isEmpty()) {
                        repository.channelVideos(url, page).let { c.copy(videos = c.videos + it.items, nextPage = it.nextPage) }
                    } else {
                        innerTube.channelVideosSimple(channel.id, tab.params, page)
                            .let { c.copy(videos = c.videos + it.items, nextPage = it.nextPage) }
                    }
                    ChannelTabKind.Live -> innerTube.channelVideosSimple(channel.id, tab.params, page)
                        .let { c.copy(videos = c.videos + it.items, nextPage = it.nextPage) }
                    ChannelTabKind.Playlists -> innerTube.channelPlaylists(channel.id, tab.params, page)
                        .let { c.copy(playlists = c.playlists + it.items, nextPage = it.nextPage) }
                    ChannelTabKind.Posts -> innerTube.channelPosts(channel.id, tab.params, page)
                        .let { c.copy(posts = c.posts + it.items, nextPage = it.nextPage) }
                    ChannelTabKind.Home -> c.copy(nextPage = null)
                }
            }.onSuccess { next ->
                updateContent(kind) {
                    next.copy(
                        loadingMore = false,
                        videos = next.videos.distinctBy { v -> v.url },
                        playlists = next.playlists.distinctBy { p -> p.id },
                        posts = next.posts.distinctBy { p -> p.id },
                    )
                }
            }.onFailure { updateContent(kind) { it.copy(loadingMore = false, nextPage = null) } }
        }
    }

    /** Returns false when the user must sign in first. */
    fun setSubscribed(subscribe: Boolean): Boolean {
        if (!account.isLoggedIn) return false
        val channel = _state.value.channel ?: return true
        val previous = _state.value.subscribed
        _state.update { it.copy(subscribed = subscribe) }
        viewModelScope.launch {
            runCatching { innerTube.setSubscribed(channel.id, subscribe) }
                .onSuccess { _state.update { it.copy(message = if (subscribe) "Abonnement ajouté" else "Abonnement supprimé") } }
                .onFailure { e -> _state.update { it.copy(subscribed = previous, message = e.message ?: "Action impossible") } }
        }
        return true
    }

    fun setHidden(hidden: Boolean) {
        val channel = _state.value.channel ?: return
        hiddenChannels.setHidden(channel.id, hidden)
        _state.update {
            it.copy(message = if (hidden) "Cette chaîne n'apparaîtra plus dans tes fils" else "La chaîne n'est plus masquée")
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    viewModel: ChannelViewModel,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onLoginRequired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val channel = state.channel
    val isHidden = channel != null && channel.id in hidden
    var menuOpen by remember { mutableStateOf(false) }
    var confirmUnsubscribe by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(channel?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    if (channel != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Plus d'options") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Partager") },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                    onClick = {
                                        menuOpen = false
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, channel.name)
                                            putExtra(Intent.EXTRA_TEXT, channel.url)
                                        }
                                        context.startActivity(Intent.createChooser(send, null))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isHidden) "Ne plus masquer la chaîne" else "Masquer la chaîne") },
                                    leadingIcon = { Icon(if (isHidden) Icons.Outlined.Visibility else Icons.Outlined.Block, null) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.setHidden(!isHidden)
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            state.isLoading -> LoadingState(modifier)
            state.error != null || channel == null ->
                ErrorState(state.error ?: IllegalStateException(), onRetry = viewModel::load, modifier = modifier)
            else -> {
                val listState = rememberLazyListState()
                LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)
                val selected = state.selected
                val content = selected?.let { state.content[it] }
                LazyColumn(modifier, state = listState) {
                    item(key = "header") {
                        Header(
                            channel = channel,
                            subscribed = state.subscribed,
                            isHidden = isHidden,
                            onSubscribe = {
                                if (state.subscribed == true) confirmUnsubscribe = true
                                else if (!viewModel.setSubscribed(true)) onLoginRequired()
                            },
                            onUnhide = { viewModel.setHidden(false) },
                        )
                    }
                    stickyHeader(key = "tabs") {
                        if (state.tabs.size > 1 && selected != null) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = state.tabs.indexOfFirst { it.kind == selected }.coerceAtLeast(0),
                                edgePadding = 4.dp,
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                            ) {
                                state.tabs.forEach { tab ->
                                    Tab(
                                        selected = tab.kind == selected,
                                        onClick = { viewModel.select(tab.kind) },
                                        text = { Text(tab.kind.label, style = MaterialTheme.typography.titleSmall) },
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (selected != null && content != null) {
                        tabContent(selected, content, viewModel, onVideoClick, onPlaylistClick)
                    }
                }
            }
        }
    }

    if (confirmUnsubscribe && channel != null) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            text = { Text("Se désabonner de ${channel.name} ?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsubscribe = false
                    viewModel.setSubscribed(false)
                }) { Text("Se désabonner") }
            },
            dismissButton = { TextButton(onClick = { confirmUnsubscribe = false }) { Text("Annuler") } },
        )
    }
}

private fun LazyListScope.tabContent(
    kind: ChannelTabKind,
    content: TabContent,
    viewModel: ChannelViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onPlaylistClick: (String) -> Unit,
) {
    if (kind == ChannelTabKind.Videos && content.sorts.isNotEmpty()) {
        item(key = "sorts") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(content.sorts, key = { it.label }) { chip -> SortChipView(chip.label, chip.selected) { viewModel.sort(chip) } }
            }
        }
    }
    when {
        content.loading -> item(key = "loading") { ListFooterLoader() }
        content.error != null -> item(key = "error") {
            Text(
                content.error.userMessage(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> when (kind) {
            ChannelTabKind.Home -> {
                if (content.shelves.isEmpty()) item(key = "empty") { EmptyTab() }
                content.shelves.forEachIndexed { index, shelf ->
                    item(key = "shelf-$index") { ShelfRow(shelf, onVideoClick, onPlaylistClick) }
                }
            }
            ChannelTabKind.Videos, ChannelTabKind.Live -> {
                if (content.videos.isEmpty()) item(key = "empty") { EmptyTab() }
                items(content.videos, key = { it.url }) { video ->
                    VideoCard(video, onClick = { onVideoClick(video) }, onChannelClick = null)
                }
            }
            ChannelTabKind.Playlists -> {
                if (content.playlists.isEmpty()) item(key = "empty") { EmptyTab() }
                items(content.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist,
                        onClick = { onPlaylistClick(playlist.url) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            ChannelTabKind.Posts -> {
                if (content.posts.isEmpty()) item(key = "empty") { EmptyTab() }
                items(content.posts, key = { it.id }) { post -> PostCard(post, onVideoClick) }
            }
        }
    }
    if (content.loadingMore) item(key = "more") { ListFooterLoader() }
}

@Composable
private fun Header(
    channel: ChannelDetails,
    subscribed: Boolean?,
    isHidden: Boolean,
    onSubscribe: () -> Unit,
    onUnhide: () -> Unit,
) {
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
                Text(channel.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Format.subscribers(channel.subscriberCount)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (channel.description.isNotBlank()) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        SubscribeButton(subscribed == true, onSubscribe, Modifier.padding(12.dp).fillMaxWidth())
        if (isHidden) {
            Row(
                Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chaîne masquée de tes fils", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onUnhide) { Text("Annuler", color = MaterialTheme.colorScheme.tertiary) }
            }
        }
    }
}

@Composable
private fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (subscribed) colors.surfaceContainerHigh else colors.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (subscribed) "Abonné" else "S'abonner",
            style = MaterialTheme.typography.labelLarge,
            color = if (subscribed) colors.onSurface else colors.onPrimary,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun SortChipView(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun ShelfRow(shelf: ChannelShelf, onVideoClick: (VideoItem) -> Unit, onPlaylistClick: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(shelf.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.videos, key = { it.url }) { video -> VideoShelfCard(video, onClick = { onVideoClick(video) }) }
            items(shelf.playlists, key = { it.id }) { playlist ->
                PlaylistCard(playlist, onClick = { onPlaylistClick(playlist.url) }, modifier = Modifier.width(260.dp))
            }
        }
    }
}

@Composable
private fun PostCard(post: ChannelPost, onVideoClick: (VideoItem) -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorAvatar, 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(post.author.orEmpty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
            post.publishedText?.let { Text("  $it", style = MaterialTheme.typography.bodySmall, color = secondary) }
        }
        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(post.text, style = MaterialTheme.typography.bodyMedium)
        }
        post.images.firstOrNull()?.let { image ->
            Spacer(Modifier.height(10.dp))
            Box {
                AsyncImage(
                    image,
                    null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                )
                if (post.images.size > 1) {
                    Text(
                        "1/${post.images.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        post.video?.let { video ->
            Spacer(Modifier.height(10.dp))
            VideoShelfCard(video, onClick = { onVideoClick(video) }, modifier = Modifier.fillMaxWidth())
        }
        if (post.likesText != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, null, Modifier.size(16.dp), tint = secondary)
                Text("  ${post.likesText}", style = MaterialTheme.typography.bodySmall, color = secondary)
            }
        }
    }
}

@Composable
private fun EmptyTab() {
    Text(
        "Rien à afficher ici pour le moment.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}
