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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import app.sigmatube.ui.search.SearchViewModel
import app.sigmatube.ui.watch.WatchOverlay
import app.sigmatube.ui.watch.WatchSheetState
import app.sigmatube.ui.watch.WatchViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object HomeRoute
@Serializable data object SubscriptionsRoute
@Serializable data object LibraryRoute
@Serializable data object SearchRoute
@Serializable data object LoginRoute
@Serializable data class ChannelRoute(val url: String)

private data class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, "Accueil", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(SubscriptionsRoute, SubscriptionsRoute::class, "Abonnements", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
    Tab(LibraryRoute, LibraryRoute::class, "Vous", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
)

private fun NavDestination?.isTab(tab: Tab) = this?.hasRoute(tab.routeClass) == true

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
    val showBottomBar = destination?.hasRoute(SearchRoute::class) != true && destination?.hasRoute(LoginRoute::class) != true

    // Activity-scoped (we're outside any NavHost destination here).
    val watch: WatchViewModel = viewModel { WatchViewModel(repository, container.playerManager, container.innerTube, container.account) }
    val watchState by watch.state.collectAsStateWithLifecycle()
    val sheet = remember { WatchSheetState() }
    val scope = rememberCoroutineScope()
    val openVideo: (String) -> Unit = { url ->
        watch.open(url)
        scope.launch { sheet.expand() }
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

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                BottomBar(
                    destination,
                    Modifier.onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
                ) { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = viewModel { HomeViewModel(repository, container.innerTube, container.account) },
                    onVideoClick = { openVideo(it.url) },
                    onChannelClick = { navController.navigate(ChannelRoute(it)) },
                    onSearchClick = { navController.navigate(SearchRoute) },
                )
            }
            composable<SubscriptionsRoute> {
                SubscriptionsScreen(
                    account = container.account,
                    viewModel = viewModel { SubscriptionsViewModel(container.innerTube) },
                    onLogin = { navController.navigate(LoginRoute) },
                    onVideoClick = { openVideo(it.url) },
                    onChannelClick = { navController.navigate(ChannelRoute(it)) },
                )
            }
            composable<LibraryRoute> {
                LibraryScreen(container.account, container.innerTube, onLogin = { navController.navigate(LoginRoute) })
            }
            composable<LoginRoute> {
                LoginScreen(container.account, onDone = { navController.popBackStack() })
            }
            composable<SearchRoute> {
                SearchScreen(
                    viewModel = viewModel { SearchViewModel(repository) },
                    onBack = navController::popBackStack,
                    onVideoClick = { openVideo(it.url) },
                    onChannelClick = { navController.navigate(ChannelRoute(it)) },
                )
            }
            composable<ChannelRoute> { entry ->
                val route = entry.toRoute<ChannelRoute>()
                ChannelScreen(
                    viewModel = viewModel { ChannelViewModel(route.url, repository) },
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
            bottomInset = if (showBottomBar) bottomBarHeight else navBarInset,
            onOpenVideo = openVideo,
            onChannelClick = { navController.navigate(ChannelRoute(it)) },
            onLoginRequired = { navController.navigate(LoginRoute) },
        )
    }
    }
}

@Composable
private fun BottomBar(destination: NavDestination?, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.navigationBarsPadding().height(56.dp)) {
                tabs.forEach { tab ->
                    val selected = destination.isTab(tab)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(tab.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
