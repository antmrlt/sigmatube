package app.sigmatube.ui.library

import androidx.compose.foundation.layout.Arrangement
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
            runCatching { innerTube.subscriptionsFeed() }
                .onSuccess { r -> _state.update { SubscriptionsUiState(videos = r.items, nextPage = r.nextPage) } }
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

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.load(refreshing = true) },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        when {
            state.isLoading -> LoadingState()
            state.error != null && state.videos.isEmpty() -> ErrorState(state.error!!, onRetry = { viewModel.load() })
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item {
                    Text(
                        "Abonnements",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(state.videos, key = { it.url }) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onChannelClick = video.channelUrl?.let { url -> { onChannelClick(url) } },
                    )
                }
                if (state.isLoadingMore) item { ListFooterLoader() }
            }
        }
    }
}

// endregion

// region "Vous"

@Composable
fun LibraryScreen(account: AccountManager, innerTube: InnerTubeClient, onLogin: () -> Unit) {
    val cookies by account.cookies.collectAsStateWithLifecycle()
    if (cookies == null) {
        SignInPrompt(
            title = "Profite de ton compte",
            message = "Connecte-toi pour retrouver ton fil d'accueil personnalisé et tes abonnements, sans pub.",
            onLogin = onLogin,
        )
        return
    }
    var info by remember { mutableStateOf<AccountInfo?>(null) }
    LaunchedEffect(cookies) { info = innerTube.accountInfo() }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(info?.avatar, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(info?.name ?: "Compte YouTube", style = MaterialTheme.typography.headlineSmall)
                info?.handle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = account::logout) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Se déconnecter")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "L'historique, les playlists et les téléchargements arriveront ici.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion

@Composable
private fun SignInPrompt(title: String, message: String, onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (title.contains("abonnement", true)) Icons.Outlined.Subscriptions else Icons.Outlined.AccountCircle,
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
