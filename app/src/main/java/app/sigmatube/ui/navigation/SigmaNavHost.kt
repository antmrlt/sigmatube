package app.sigmatube.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.sigmatube.AppContainer
import app.sigmatube.ui.channel.ChannelScreen
import app.sigmatube.ui.channel.ChannelViewModel
import app.sigmatube.ui.home.HomeScreen
import app.sigmatube.ui.home.HomeViewModel
import app.sigmatube.ui.library.LibraryScreen
import app.sigmatube.ui.library.SubscriptionsScreen
import app.sigmatube.ui.library.SubscriptionsViewModel
import app.sigmatube.ui.account.LoginScreen
import app.sigmatube.ui.search.SearchScreen
import app.sigmatube.ui.music.MusicScreen
import app.sigmatube.ui.music.MusicViewModel
import app.sigmatube.ui.music.MusicGenreScreen
import app.sigmatube.ui.music.MusicGenreViewModel
import app.sigmatube.data.MUSIC_GENRES
import app.sigmatube.ui.playlist.LocalPlaylistScreen
import app.sigmatube.ui.components.LocalVideoMenu
import app.sigmatube.ui.components.VideoMenuActions
import app.sigmatube.ui.components.VideoMenuHost
import app.sigmatube.ui.components.VideoMenuState
import app.sigmatube.data.VideoItem
import app.sigmatube.data.videoIdOf
import app.sigmatube.data.auth.channelIdFromUrl
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import app.sigmatube.ui.search.SearchViewModel
import app.sigmatube.ui.watch.WatchOverlay
import app.sigmatube.ui.playlist.PlaylistScreen
import app.sigmatube.ui.playlist.PlaylistViewModel
import app.sigmatube.data.LocalHiddenChannels
import app.sigmatube.data.LocalWatchProgress
import androidx.compose.runtime.CompositionLocalProvider
import app.sigmatube.ui.watch.WatchSheetState
import app.sigmatube.ui.watch.WatchViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.produceState
import app.sigmatube.BuildConfig
import app.sigmatube.data.auth.AccountInfo
import app.sigmatube.ui.components.AppTopBar
import app.sigmatube.ui.components.Avatar
import app.sigmatube.ui.library.HISTORY
import app.sigmatube.ui.library.VideoListScreen
import app.sigmatube.ui.settings.SettingsScreen
import app.sigmatube.ui.watch.LocalSeekMs
import kotlinx.serialization.Serializable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import app.sigmatube.ui.components.rememberPopScale
import kotlin.math.roundToInt
import app.sigmatube.ui.theme.AppIcons

/** The swipeable top-level pages (Accueil / Abonnements / Musique / Vous). Each one has its own search. */
@Serializable data object TabsRoute
@Serializable data object LoginRoute
@Serializable data class ChannelRoute(val url: String)
@Serializable data class PlaylistRoute(val url: String)
@Serializable data object SettingsRoute
@Serializable data class VideoListRoute(val kind: String)
/** [fromMusic]: opened from the Musique tab, so playing it uses the music player page. */
@Serializable data class LocalPlaylistRoute(val id: String, val fromMusic: Boolean = false)
/** A music style page, from "Explorer les styles". */
@Serializable data class MusicGenreRoute(val id: String)

private data class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("Accueil", AppIcons.Home, AppIcons.HomeFilled),
    Tab("Abonnements", AppIcons.Subscriptions, AppIcons.SubscriptionsFilled),
    Tab("Musique", AppIcons.MusicNote, AppIcons.MusicNoteFilled),
    Tab("Vous", AppIcons.AccountCircle, AppIcons.AccountCircleFilled),
)

@Composable
fun SigmaNavHost(
    container: AppContainer,
    pendingVideoUrl: String?,
    onPendingVideoConsumed: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val repository = container.repository
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = listOf(LoginRoute::class, SettingsRoute::class).none { destination?.hasRoute(it) == true }

    // Activity-scoped (we're outside any NavHost destination here).
    val watch: WatchViewModel = viewModel { WatchViewModel(repository, container.playerManager, container.innerTube, container.account, container.settings, container.history, container.hiddenChannels, container.watchProgress) }
    val watchState by watch.state.collectAsStateWithLifecycle()
    val sheet = remember { WatchSheetState() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { tabs.size }
    // The music player page is only for what is started from the Musique tab (and what follows it).
    val musicTab = tabs.indexOfFirst { it.label == "Musique" }
    val inMusicContext = (destination?.hasRoute(TabsRoute::class) == true && pagerState.currentPage == musicTab) ||
        (destination?.hasRoute(LocalPlaylistRoute::class) == true && backStackEntry?.toRoute<LocalPlaylistRoute>()?.fromMusic == true) ||
        destination?.hasRoute(MusicGenreRoute::class) == true
    val musicContext by rememberUpdatedState(inMusicContext)
    /** Opens a video from a page; [music] = started from the Musique tab. */
    val openVideoAs: (String, Boolean?) -> Unit = { url, music ->
        watch.open(url, music)
        scope.launch { sheet.expand() }
    }
    val openVideo: (String) -> Unit = { url -> openVideoAs(url, false) }
    /** Plays a list (playlist, chart): the picked video now, the rest in the queue. */
    val playListAs: (List<VideoItem>, Int, Boolean, Boolean) -> Unit = { videos, start, shuffle, music ->
        watch.playAll(videos, start, shuffle, music)
        scope.launch { sheet.expand() }
    }
    val openQueue: () -> Unit = {
        sheet.queueRequests++
        scope.launch { sheet.expand() }
    }
    val context = LocalContext.current
    // One-shot messages from the player and from the video menus (shown even with no video open).
    LaunchedEffect(watchState.message) {
        watchState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            watch.consumeMessage()
        }
    }
    val videoMenu = remember { VideoMenuState() }
    val videoMenuActions = remember(watch) {
        // With nothing open, queueing a video starts it in the mini-player instead.
        fun startedMinimized(started: Boolean) { if (started) sheet.progress = 1f }
        VideoMenuActions(
            playNext = { startedMinimized(watch.playNext(it, musicContext)) },
            playLast = { startedMinimized(watch.playLast(it, musicContext)) },
            saveToWatchLater = {
                if (!watch.saveToWatchLater(it)) {
                    watch.showMessage("Connecte-toi pour enregistrer des vidéos")
                    navController.navigate(LoginRoute)
                }
            },
            download = { video, audioOnly -> container.downloads.download(video, audioOnly) },
            notInterested = { video ->
                videoIdOf(video.url)?.let { container.hiddenChannels.setVideoHidden(it, true) }
                watch.showMessage("Vidéo masquée. Tu peux l'afficher à nouveau dans les paramètres.")
            },
            dontRecommendChannel = { video ->
                channelIdFromUrl(video.channelUrl)?.let { container.hiddenChannels.setHidden(it, true) }
                watch.showMessage("La chaîne ${video.channelName ?: ""} ne te sera plus recommandée".replace("  ", " "))
            },
        )
    }

    LaunchedEffect(pendingVideoUrl) {
        if (pendingVideoUrl != null) {
            openVideo(pendingVideoUrl)
            onPendingVideoConsumed()
        }
    }

    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    val hiddenChannels by container.hiddenChannels.ids.collectAsStateWithLifecycle()
    val hiddenVideos by container.hiddenChannels.videos.collectAsStateWithLifecycle()
    val settingsState by container.settings.state.collectAsStateWithLifecycle()
    val watchedFractions by container.watchProgress.fractions.collectAsStateWithLifecycle()
    val cookies by container.account.cookies.collectAsStateWithLifecycle()
    val accountInfo by produceState<AccountInfo?>(null, cookies) {
        value = if (cookies != null) container.innerTube.accountInfo() else null
    }

    CompositionLocalProvider(
        LocalHiddenChannels provides hiddenChannels + hiddenVideos,
        LocalVideoMenu provides videoMenu,
        LocalWatchProgress provides watchedFractions,
        LocalSeekMs provides settingsState.seekSeconds * 1000L,
    ) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showBottomBar) {
                    BottomBar(
                        selected = pagerState.currentPage,
                        avatar = accountInfo?.avatar,
                        modifier = Modifier.onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
                    ) { index ->
                        // From a channel page: come back to the tabs first.
                        if (destination?.hasRoute(TabsRoute::class) != true) navController.popBackStack(TabsRoute, inclusive = false)
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = TabsRoute,
                // Pushed pages slide in a little from the right and fade; back reverses it.
                enterTransition = { slideInHorizontally(tween(280)) { it / 6 } + fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(260)) { it / 6 } + fadeOut(tween(180)) },
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                composable<TabsRoute> {
                    // One search per tab, kept while visiting the other tabs.
                    val searches = List(tabs.size) { i ->
                        viewModel(key = "search-$i") { SearchViewModel(repository, container.history) }
                    }
                    val onTabs = destination?.hasRoute(TabsRoute::class) == true
                    // The expanded watch page owns the back button and the keyboard.
                    val watchCovers = watchState.url != null && !sheet.isMinimized
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.weight(1f),
                    ) { page ->
                        val search = searches[page]
                        val searchState by search.state.collectAsStateWithLifecycle()
                        if (searchState.active) {
                            SearchScreen(
                                viewModel = search,
                                isCurrent = page == pagerState.currentPage && onTabs && !watchCovers,
                                onBack = search::close,
                                onVideoClick = { openVideoAs(it.url, page == musicTab) },
                                onChannelClick = { navController.navigate(ChannelRoute(it)) },
                            )
                            return@HorizontalPager
                        }
                        // The top bar slides away while scrolling down and comes back on the way up.
                        val topBar = remember { CollapsingBarState() }
                        Column(Modifier.fillMaxSize()) {
                        AppTopBar(
                            onSearch = search::open,
                            onSettings = if (page == tabs.lastIndex) ({ navController.navigate(SettingsRoute) }) else null,
                            withStatusBarPadding = false,
                            modifier = Modifier.collapsing(topBar),
                        )
                        Box(Modifier.weight(1f).nestedScroll(topBar.connection)) {
                        when (page) {
                            0 -> HomeScreen(
                                viewModel = viewModel { HomeViewModel(repository, container.innerTube, container.account, container.settings) },
                                onVideoClick = { openVideo(it.url) },
                                onChannelClick = { navController.navigate(ChannelRoute(it)) },
                            )
                            1 -> SubscriptionsScreen(
                                account = container.account,
                                viewModel = viewModel { SubscriptionsViewModel(container.innerTube) },
                                onLogin = { navController.navigate(LoginRoute) },
                                onVideoClick = { openVideo(it.url) },
                                onChannelClick = { navController.navigate(ChannelRoute(it)) },
                            )
                            2 -> MusicScreen(
                                viewModel = viewModel { MusicViewModel(container.innerTube, repository, container.musicPrefs, container.history) },
                                prefs = container.musicPrefs,
                                history = container.history,
                                playlists = container.playlists,
                                onOpenGenre = { navController.navigate(MusicGenreRoute(it.id)) },
                                onChannelClick = { navController.navigate(ChannelRoute(it)) },
                                queue = watchState.queue,
                                onOpenQueue = openQueue,
                                onOpenPlaylist = { navController.navigate(LocalPlaylistRoute(it, fromMusic = true)) },
                                onPlay = { v, start, shuffle -> playListAs(v, start, shuffle, true) },
                            )
                            else -> LibraryScreen(
                                account = container.account,
                                accountInfo = accountInfo,
                                history = container.history,
                                playlists = container.playlists,
                                onLogin = { navController.navigate(LoginRoute) },
                                onVideoClick = { openVideo(it.url) },
                                onOpenHistory = { navController.navigate(VideoListRoute(HISTORY)) },
                                onOpenPlaylist = { navController.navigate(VideoListRoute(it)) },
                                onOpenLocalPlaylist = { navController.navigate(LocalPlaylistRoute(it)) },
                            )
                        }
                        }
                        }
                    }
                    }
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = container.settings,
                        history = container.history,
                        hiddenChannels = container.hiddenChannels,
                        account = container.account,
                        appVersion = BuildConfig.VERSION_NAME,
                        onBack = navController::popBackStack,
                    )
                }
                composable<VideoListRoute> { entry ->
                    VideoListScreen(
                        kind = entry.toRoute<VideoListRoute>().kind,
                        history = container.history,
                        innerTube = container.innerTube,
                        onBack = navController::popBackStack,
                        onVideoClick = { openVideo(it.url) },
                    )
                }
                composable<LoginRoute> {
                    LoginScreen(container.account, onDone = { navController.popBackStack() })
                }
                composable<LocalPlaylistRoute> { entry ->
                    val route = entry.toRoute<LocalPlaylistRoute>()
                    LocalPlaylistScreen(
                        id = route.id,
                        playlists = container.playlists,
                        onBack = { navController.popBackStack() },
                        onPlay = { v, start, shuffle -> playListAs(v, start, shuffle, route.fromMusic) },
                    )
                }
                composable<MusicGenreRoute> { entry ->
                    val genre = MUSIC_GENRES.firstOrNull { it.id == entry.toRoute<MusicGenreRoute>().id } ?: MUSIC_GENRES.first()
                    MusicGenreScreen(
                        genre = genre,
                        viewModel = viewModel { MusicGenreViewModel(genre.query, repository) },
                        onBack = { navController.popBackStack() },
                        onPlay = { v, start, shuffle -> playListAs(v, start, shuffle, true) },
                    )
                }
                composable<ChannelRoute> { entry ->
                    val route = entry.toRoute<ChannelRoute>()
                    ChannelScreen(
                        viewModel = viewModel {
                            ChannelViewModel(route.url, repository, container.innerTube, container.account, container.hiddenChannels)
                        },
                        onBack = navController::popBackStack,
                        onVideoClick = { openVideo(it.url) },
                        onPlaylistClick = { navController.navigate(PlaylistRoute(it)) },
                        onLoginRequired = { navController.navigate(LoginRoute) },
                    )
                }
                composable<PlaylistRoute> { entry ->
                    val route = entry.toRoute<PlaylistRoute>()
                    PlaylistScreen(
                        viewModel = viewModel { PlaylistViewModel(route.url, repository) },
                        onBack = navController::popBackStack,
                        onVideoClick = { openVideo(it.url) },
                    )
                }
            }
        }

        if (watchState.url != null) {
            WatchOverlay(
                viewModel = watch,
                sheet = sheet,
                // Keyboard open (e.g. typing a search): the mini-player rests above it.
                bottomInset = maxOf(if (showBottomBar) bottomBarHeight else navBarInset, imeInset),
                onOpenVideo = { url -> openVideoAs(url, null) },
                onChannelClick = { navController.navigate(ChannelRoute(it)) },
                onLoginRequired = { navController.navigate(LoginRoute) },
            )
        }
        VideoMenuHost(videoMenu, videoMenuActions, container.playlists)
    }
    }
}

@Composable
private fun BottomBar(selected: Int, avatar: String?, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.navigationBarsPadding().height(54.dp)) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // A pill grows behind the selected icon, and the icon pops when it gets selected.
                        val pillWidth by animateDpAsState(
                            if (isSelected) 60.dp else 34.dp,
                            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                            label = "pill",
                        )
                        val pillAlpha by animateFloatAsState(if (isSelected) 1f else 0f, tween(200), label = "pillAlpha")
                        val pop = rememberPopScale(isSelected, enabled = isSelected)
                        Box(Modifier.height(34.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(width = pillWidth, height = 34.dp)
                                    .graphicsLayer { alpha = pillAlpha }
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                            )
                            Box(Modifier.graphicsLayer { scaleX = pop; scaleY = pop }, contentAlignment = Alignment.Center) {
                                if (index == tabs.lastIndex && avatar != null) {
                                    Avatar(
                                        avatar,
                                        27.dp,
                                        if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier,
                                    )
                                } else {
                                    Crossfade(isSelected, animationSpec = tween(180), label = "icon") { on ->
                                        Icon(if (on) tab.selectedIcon else tab.icon, contentDescription = tab.label, modifier = Modifier.size(27.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Offset (0 = fully shown, -height = hidden) of a bar that collapses with scrolling. */
@Stable
private class CollapsingBarState {
    var offset by mutableFloatStateOf(0f)
    var height = 0f
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (height > 0f) offset = (offset + available.y).coerceIn(-height, 0f)
            return Offset.Zero
        }
    }
}

/** Shrinks the layout by the bar's offset so the content below follows it up. */
private fun Modifier.collapsing(state: CollapsingBarState) = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    state.height = placeable.height.toFloat()
    val offset = state.offset.roundToInt()
    val visible = (placeable.height + offset).coerceAtLeast(0)
    layout(placeable.width, visible) { placeable.place(0, offset) }
}
