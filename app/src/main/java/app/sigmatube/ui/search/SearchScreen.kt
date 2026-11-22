package app.sigmatube.ui.search

import app.sigmatube.ui.components.SkeletonFeed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.LocalHistory
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
import app.sigmatube.data.withoutHidden
import app.sigmatube.ui.components.ErrorState
import app.sigmatube.ui.components.ListFooterLoader
import app.sigmatube.ui.components.LoadMoreEffect
import app.sigmatube.ui.components.LoadingState
import app.sigmatube.ui.components.MessageState
import app.sigmatube.ui.components.VideoCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class SearchUiState(
    /** Whether this tab currently shows its search (each tab keeps its own). */
    val active: Boolean = false,
    val query: String = "",
    /** Query the current results belong to; null while the user is still typing. */
    val submittedQuery: String? = null,
    val suggestions: List<String> = emptyList(),
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
    val nextPage: Page? = null,
    /** Musique tab: YouTube Music results instead of the video feed. */
    val music: app.sigmatube.data.auth.MusicSearchResult? = null,
)

class SearchViewModel(
    private val repository: YouTubeRepository,
    private val history: LocalHistory,
    /** Set for the Musique tab: searches YouTube Music (songs, artists, albums). */
    private val musicClient: app.sigmatube.data.auth.InnerTubeClient? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state
    val searchHistory = history.searches

    private var suggestionJob: Job? = null
    private var searchJob: Job? = null

    fun open() = _state.update { if (it.active) it else SearchUiState(active = true) }

    /** Leaves the search: the tab shows its own content again. */
    fun close() {
        suggestionJob?.cancel()
        searchJob?.cancel()
        _state.value = SearchUiState()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, submittedQuery = null) }
        suggestionJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionJob = viewModelScope.launch {
            delay(200) // debounce keystrokes
            val suggestions = runCatching { repository.suggestions(query) }.getOrDefault(emptyList())
            _state.update { it.copy(suggestions = suggestions) }
        }
    }

    fun submit(query: String = _state.value.query) {
        val q = query.trim()
        if (q.isEmpty()) return
        history.addSearch(q)
        suggestionJob?.cancel()
        searchJob?.cancel()
        _state.update {
            it.copy(query = q, submittedQuery = q, isLoading = true, error = null, results = emptyList(), nextPage = null, music = null)
        }
        searchJob = viewModelScope.launch {
            if (musicClient != null) {
                runCatching { musicClient.musicSearch(q) }
                    .onSuccess { r -> _state.update { it.copy(music = r, isLoading = false) } }
                    .onFailure { e -> _state.update { it.copy(error = e, isLoading = false) } }
                return@launch
            }
            runCatching { repository.search(q) }
                .onSuccess { r -> _state.update { it.copy(results = r.items, nextPage = r.nextPage, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(error = e, isLoading = false) } }
        }
    }

    fun removeFromHistory(query: String) = history.removeSearch(query)

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        val q = s.submittedQuery ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        searchJob = viewModelScope.launch {
            runCatching { repository.search(q, page) }
                .onSuccess { r ->
                    _state.update {
                        it.copy(
                            results = (it.results + r.items).distinctBy { v -> v.url },
                            nextPage = r.nextPage,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoadingMore = false, nextPage = null) } }
        }
    }
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    /** True while this tab is the one on screen (focus and keyboard follow it). */
    isCurrent: Boolean,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
    placeholder: String = "Rechercher sur YouTube",
    /** Musique tab: how to open songs, artists and albums from the music results. */
    musicNav: app.sigmatube.ui.music.MusicNavigation? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val hiddenIds = LocalHiddenChannels.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var hasFocus by remember { mutableStateOf(false) }

    // Back on a tab whose search was being typed: the keyboard comes back. Leaving it: it goes away.
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            if (viewModel.state.value.submittedQuery == null) runCatching { focusRequester.requestFocus() }
        } else if (hasFocus) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    BackHandler(enabled = isCurrent, onBack = onBack)

    fun submit(q: String) {
        keyboard?.hide()
        // Search done: the field lets go of the focus (no cursor, keyboard stays down).
        focusManager.clearFocus()
        viewModel.submit(q)
    }

    Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    onSearch = { submit(state.query) },
                    onClear = {
                        viewModel.onQueryChange("")
                        focusRequester.requestFocus()
                    },
                    placeholder = placeholder,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { hasFocus = it.isFocused },
                )
            }
        val modifier = Modifier.weight(1f).fillMaxWidth()
        when {
            state.submittedQuery == null -> Suggestions(
                query = state.query,
                history = searchHistory,
                suggestions = state.suggestions,
                onPick = ::submit,
                onFill = viewModel::onQueryChange,
                onRemove = viewModel::removeFromHistory,
                modifier = modifier,
            )
            state.isLoading && musicNav != null -> Column(modifier) { repeat(8) { app.sigmatube.ui.music.SongRowSkeleton() } }
            state.isLoading -> SkeletonFeed(modifier)
            state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.submit() }, modifier = modifier)
            state.music != null && musicNav != null -> app.sigmatube.ui.music.MusicSearchResults(state.music!!, musicNav, modifier)
            state.results.isEmpty() -> MessageState(
                title = "Aucun résultat",
                message = "Essaie avec d'autres mots-clés.",
                icon = Icons.Rounded.SearchOff,
                modifier = modifier,
            )
            else -> {
                val listState = rememberLazyListState()
                LoadMoreEffect(listState, onLoadMore = viewModel::loadMore)
                LazyColumn(modifier, state = listState) {
                    items(state.results.withoutHidden(hiddenIds), key = { it.url }) { video ->
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
}

/** Slim 40dp pill, like YouTube's search box. */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Effacer", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Suggestions(
    query: String,
    history: List<String>,
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val q = query.trim()
    // History first (matching what's typed), then live suggestions not already shown.
    val pastMatches = if (q.isEmpty()) history else history.filter { it.contains(q, ignoreCase = true) }.take(3)
    val live = suggestions.filterNot { s -> pastMatches.any { it.equals(s, ignoreCase = true) } }

    LazyColumn(modifier) {
        items(pastMatches, key = { "h:$it" }) { entry ->
            SuggestionRow(
                icon = Icons.Outlined.History,
                text = highlightCompletion(q, entry),
                onClick = { onPick(entry) },
                onLongClick = { confirmRemove = entry },
                onFill = { onFill("$entry ") },
            )
        }
        items(live, key = { "s:$it" }) { suggestion ->
            SuggestionRow(
                icon = Icons.Outlined.Search,
                text = highlightCompletion(q, suggestion),
                onClick = { onPick(suggestion) },
                onLongClick = null,
                onFill = { onFill("$suggestion ") },
            )
        }
    }

    confirmRemove?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(entry) },
            text = { Text("Supprimer de l'historique des recherches ?") },
            confirmButton = { TextButton(onClick = { onRemove(entry); confirmRemove = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionRow(
    icon: ImageVector,
    text: AnnotatedString,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFill: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(icon, null)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        IconButton(onClick = onFill) { Icon(Icons.Outlined.NorthWest, "Compléter") }
    }
}

/** Typed prefix in regular weight, YouTube-style bold for the suggested completion. */
private fun highlightCompletion(query: String, suggestion: String): AnnotatedString = buildAnnotatedString {
    if (query.isNotEmpty() && suggestion.startsWith(query, ignoreCase = true)) {
        append(suggestion.substring(0, query.length))
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(suggestion.substring(query.length)) }
    } else {
        withStyle(SpanStyle(fontWeight = if (query.isEmpty()) FontWeight.Normal else FontWeight.Medium)) { append(suggestion) }
    }
}
