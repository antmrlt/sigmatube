package app.sigmatube.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sigmatube.data.VideoItem
import app.sigmatube.data.YouTubeRepository
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
    val query: String = "",
    /** Query the current results belong to; null while the user is still typing. */
    val submittedQuery: String? = null,
    val suggestions: List<String> = emptyList(),
    val results: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
    val nextPage: Page? = null,
)

class SearchViewModel(private val repository: YouTubeRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private var suggestionJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, submittedQuery = null) }
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(250) // debounce keystrokes
            val suggestions = runCatching { repository.suggestions(query) }.getOrDefault(emptyList())
            _state.update { it.copy(suggestions = suggestions) }
        }
    }

    fun submit(query: String = _state.value.query) {
        val q = query.trim()
        if (q.isEmpty()) return
        suggestionJob?.cancel()
        searchJob?.cancel()
        _state.update {
            it.copy(query = q, submittedQuery = q, isLoading = true, error = null, results = emptyList(), nextPage = null)
        }
        searchJob = viewModelScope.launch {
            runCatching { repository.search(q) }
                .onSuccess { r -> _state.update { it.copy(results = r.items, nextPage = r.nextPage, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(error = e, isLoading = false) } }
        }
    }

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
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (viewModel.state.value.submittedQuery == null) focusRequester.requestFocus()
    }

    fun submit(q: String) {
        keyboard?.hide()
        viewModel.submit(q)
    }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Rechercher sur YouTube") },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onQueryChange("")
                                focusRequester.requestFocus()
                            }) { Icon(Icons.Rounded.Close, "Effacer") }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(state.query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when {
            state.submittedQuery == null -> Suggestions(
                query = state.query,
                suggestions = state.suggestions,
                onPick = ::submit,
                onFill = viewModel::onQueryChange,
                modifier = modifier,
            )
            state.isLoading -> LoadingState(modifier)
            state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.submit() }, modifier = modifier)
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
                    items(state.results, key = { it.url }) { video ->
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

@Composable
private fun Suggestions(
    query: String,
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(suggestions) { suggestion ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Icon(Icons.Outlined.Search, null)
                Text(highlightCompletion(query, suggestion), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onFill("$suggestion ") }) {
                    Icon(Icons.Outlined.NorthWest, "Compléter")
                }
            }
        }
    }
}

/** Typed prefix in regular weight, YouTube-style bold for the suggested completion. */
private fun highlightCompletion(query: String, suggestion: String): AnnotatedString = buildAnnotatedString {
    val q = query.trim()
    if (q.isNotEmpty() && suggestion.startsWith(q, ignoreCase = true)) {
        append(suggestion.substring(0, q.length))
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(suggestion.substring(q.length)) }
    } else {
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(suggestion) }
    }
}
