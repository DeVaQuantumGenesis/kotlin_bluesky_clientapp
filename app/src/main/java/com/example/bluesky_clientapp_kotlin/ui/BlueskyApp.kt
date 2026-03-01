package com.example.bluesky_clientapp_kotlin.ui

import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bluesky_clientapp_kotlin.R
import com.example.bluesky_clientapp_kotlin.data.model.ActorUi
import com.example.bluesky_clientapp_kotlin.data.model.NotificationUi
import com.example.bluesky_clientapp_kotlin.data.model.PostMediaType
import com.example.bluesky_clientapp_kotlin.data.model.PostMediaUi
import com.example.bluesky_clientapp_kotlin.data.model.PostUi
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

private data class UiCustomization(
    val motionScale: Float = 1f,
    val compactCards: Boolean = false,
    val expressiveBackground: Float = 0.72f,
    val quickGestures: Boolean = true
)

private enum class HomeFeedMode {
    Discover,
    Following
}

private enum class SearchResultTab {
    Users,
    Posts
}

private enum class ProfileSection {
    Posts,
    Replies,
    Media,
    Likes
}

private enum class ProfileRelationSheet {
    Followers,
    Follows
}

private const val UrlAnnotationTag = "url"
private val CompactPrimaryTabs = listOf(AppTab.Home, AppTab.Search, AppTab.Notifications, AppTab.Profile)
private val RailTabs = listOf(
    AppTab.Home,
    AppTab.Search,
    AppTab.Notifications,
    AppTab.Profile,
    AppTab.Settings,
    AppTab.Xrpc
)

private fun appTabIcon(tab: AppTab): ImageVector {
    return when (tab) {
        AppTab.Home -> Icons.Default.Home
        AppTab.Search -> Icons.Default.Search
        AppTab.Notifications -> Icons.Default.Notifications
        AppTab.Profile -> Icons.Default.Person
        AppTab.Settings -> Icons.Default.Settings
        AppTab.Xrpc -> Icons.Default.Explore
    }
}

@Composable
private fun appTabLabel(tab: AppTab): String {
    return when (tab) {
        AppTab.Home -> stringResource(R.string.tab_home)
        AppTab.Search -> stringResource(R.string.tab_search)
        AppTab.Notifications -> stringResource(R.string.tab_notifications)
        AppTab.Profile -> stringResource(R.string.tab_profile)
        AppTab.Settings -> stringResource(R.string.tab_settings)
        AppTab.Xrpc -> stringResource(R.string.tab_xrpc)
    }
}

private fun normalizeUrl(url: String): String {
    return if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        "https://$url"
    }
}

@Composable
@Suppress("DEPRECATION")
private fun LinkifiedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onNonLinkClick: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedText = remember(text, linkColor) {
        buildAnnotatedString {
            append(text)
            Patterns.WEB_URL.matcher(text).apply {
                while (find()) {
                    val start = start()
                    val end = end()
                    val url = text.substring(start, end)
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = start,
                        end = end
                    )
                    addStringAnnotation(
                        tag = UrlAnnotationTag,
                        annotation = url,
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    ClickableText(
        text = annotatedText,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            val link = annotatedText
                .getStringAnnotations(tag = UrlAnnotationTag, start = offset, end = offset)
                .firstOrNull()
                ?.item
            if (link != null) {
                uriHandler.openUri(normalizeUrl(link))
            } else {
                onNonLinkClick?.invoke()
            }
        }
    )
}

@Composable
fun BlueskyClientApp(viewModel: MainViewModel = viewModel()) {
    val state = viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var selectedMedia by remember { mutableStateOf<PostMediaUi?>(null) }
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { selectedUri ->
        if (selectedUri != null) {
            viewModel.setComposerMedia(selectedUri)
        }
    }
    var motionScale by rememberSaveable { mutableFloatStateOf(1f) }
    var compactCards by rememberSaveable { mutableStateOf(false) }
    var expressiveBackground by rememberSaveable { mutableFloatStateOf(0.72f) }
    var quickGestures by rememberSaveable { mutableStateOf(true) }
    val customization = UiCustomization(
        motionScale = motionScale,
        compactCards = compactCards,
        expressiveBackground = expressiveBackground,
        quickGestures = quickGestures
    )

    LaunchedEffect(state.message) {
        if (state.session == null) return@LaunchedEffect
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.oauthLoginUrl) {
        val oauthUrl = state.oauthLoginUrl ?: return@LaunchedEffect
        runCatching { uriHandler.openUri(oauthUrl) }
            .onSuccess { viewModel.consumeOAuthLoginUrl() }
            .onFailure { viewModel.onOAuthBrowserLaunchFailed() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (state.isSessionRestoring && state.session == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveCircularLoadingIndicator(
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (state.session == null) {
            LoginScreen(
                message = state.message,
                loading = state.isBusy,
                onLogin = viewModel::login
            )
        } else {
            MainScaffold(
                state = state,
                snackbarHostState = snackbarHostState,
                onTabSelected = viewModel::setTab,
                onLogout = viewModel::logout,
                onRefresh = viewModel::refreshHome,
                onOpenComposer = viewModel::openComposer,
                onSearch = viewModel::search,
                onSelectProfile = viewModel::selectProfile,
                onSelectProfileByHandle = viewModel::selectProfileByHandle,
                onToggleFollow = viewModel::toggleFollow,
                onToggleLike = viewModel::toggleLike,
                onToggleRepost = viewModel::toggleRepost,
                onDeletePost = viewModel::deletePost,
                onOpenPostThread = viewModel::openPostThread,
                onOpenMedia = { media -> selectedMedia = media },
                onSearchQueryChange = viewModel::updateSearchQuery,
                onLoadMoreTimeline = viewModel::loadMoreTimeline,
                onRawMethodChanged = viewModel::updateRawMethod,
                onRawEndpointChanged = viewModel::updateRawEndpoint,
                onRawParamsChanged = viewModel::updateRawParams,
                onRawBodyChanged = viewModel::updateRawBody,
                onRunRawXrpc = viewModel::runRawXrpc,
                customization = customization,
                onMotionScaleChange = { motionScale = it },
                onCompactCardsChange = { compactCards = it },
                onExpressiveBackgroundChange = { expressiveBackground = it },
                onQuickGesturesChange = { quickGestures = it }
            )
        }
    }

    if (state.composerOpen) {
        ComposerSheet(
            value = state.composerText,
            composerMedia = state.composerMedia,
            maxLength = 300,
            onValueChange = viewModel::updateComposerText,
            onPickMedia = {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onClearMedia = viewModel::clearComposerMedia,
            onDismiss = viewModel::closeComposer,
            onSubmit = viewModel::submitPost
        )
    }

    if (state.selectedPostThreadRoot != null) {
        PostThreadSheet(
            rootPost = state.selectedPostThreadRoot,
            replies = state.selectedPostThreadReplies,
            isLoading = state.isPostThreadLoading,
            postTranslations = state.postTranslations,
            onDismiss = viewModel::closePostThread,
            onToggleLike = viewModel::toggleLike,
            onToggleRepost = viewModel::toggleRepost,
            onDeletePost = viewModel::deletePost,
            onPreparePostTranslation = viewModel::preparePostTranslation,
            onTranslatePost = viewModel::translatePostToAppLanguage,
            onOpenMedia = { media -> selectedMedia = media }
        )
    }

    if (selectedMedia != null) {
        MediaViewerFullscreen(
            media = requireNotNull(selectedMedia),
            onDismiss = { selectedMedia = null }
        )
    }
}


@Composable
private fun LoginScreen(
    message: String?,
    loading: Boolean,
    onLogin: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 18.dp, bottomEnd = 32.dp, bottomStart = 18.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onLogin,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (loading) {
                        ExpressiveCircularLoadingIndicator(
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(stringResource(R.string.login_button))
                    }
                }
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onTabSelected: (AppTab) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onOpenComposer: () -> Unit,
    onSearch: () -> Unit,
    onSelectProfile: (ActorUi) -> Unit,
    onSelectProfileByHandle: (String) -> Unit,
    onToggleFollow: (ActorUi) -> Unit,
    onToggleLike: (PostUi) -> Unit,
    onToggleRepost: (PostUi) -> Unit,
    onDeletePost: (PostUi) -> Unit,
    onOpenPostThread: (PostUi) -> Unit,
    onOpenMedia: (PostMediaUi) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLoadMoreTimeline: () -> Unit,
    onRawMethodChanged: (RawHttpMethod) -> Unit,
    onRawEndpointChanged: (String) -> Unit,
    onRawParamsChanged: (String) -> Unit,
    onRawBodyChanged: (String) -> Unit,
    onRunRawXrpc: () -> Unit,
    customization: UiCustomization,
    onMotionScaleChange: (Float) -> Unit,
    onCompactCardsChange: (Boolean) -> Unit,
    onExpressiveBackgroundChange: (Float) -> Unit,
    onQuickGesturesChange: (Boolean) -> Unit
) {
    var homeFeedMode by rememberSaveable { mutableStateOf(HomeFeedMode.Discover) }
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val wideRailState = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useWideRail = maxWidth >= 840.dp
        val shortBarArrangement =
            if (maxWidth >= 600.dp) ShortNavigationBarArrangement.Centered
            else ShortNavigationBarArrangement.EqualWeight
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !useWideRail,
            drawerContent = {
                ExpressiveSidebar(
                    activeTab = state.activeTab,
                    profile = state.selfProfile,
                    handle = state.session?.handle.orEmpty(),
                    followersCount = state.selfFollowers.size,
                    followsCount = state.selfFollows.size,
                    onNavigate = { tab ->
                        onTabSelected(tab)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    AppTopBar(
                        activeTab = state.activeTab,
                        handle = state.session?.handle.orEmpty(),
                        onMenuClick = {
                            if (useWideRail) {
                                scope.launch {
                                    if (wideRailState.targetValue == WideNavigationRailValue.Expanded) {
                                        wideRailState.collapse()
                                    } else {
                                        wideRailState.expand()
                                    }
                                }
                            } else {
                                scope.launch { drawerState.open() }
                            }
                        },
                        onQuickNavigate = onTabSelected
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = onOpenComposer,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_post))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.post_button))
                    }
                },
                bottomBar = {
                    if (!useWideRail) {
                        ShortNavigationBar(
                            arrangement = shortBarArrangement,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f)
                        ) {
                            CompactPrimaryTabs.forEach { tab ->
                                ShortNavigationBarItem(
                                    selected = state.activeTab == tab,
                                    onClick = { onTabSelected(tab) },
                                    iconPosition = if (shortBarArrangement == ShortNavigationBarArrangement.Centered) {
                                        NavigationItemIconPosition.Start
                                    } else {
                                        NavigationItemIconPosition.Top
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = appTabIcon(tab),
                                            contentDescription = appTabLabel(tab)
                                        )
                                    },
                                    label = { Text(appTabLabel(tab)) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useWideRail) {
                        WideNavigationRail(
                            state = wideRailState,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            RailTabs.forEach { tab ->
                                WideNavigationRailItem(
                                    railExpanded = wideRailState.targetValue == WideNavigationRailValue.Expanded,
                                    selected = state.activeTab == tab,
                                    onClick = { onTabSelected(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = appTabIcon(tab),
                                            contentDescription = appTabLabel(tab)
                                        )
                                    },
                                    label = { Text(appTabLabel(tab)) }
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isBusy) {
                            ExpressiveLoadingStrip(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Crossfade(
                            targetState = state.activeTab,
                            modifier = Modifier.fillMaxSize(),
                            animationSpec = tween(
                                durationMillis = (220 * customization.motionScale.coerceAtLeast(0.35f)).toInt(),
                                easing = FastOutSlowInEasing
                            ),
                            label = "main-tab"
                        ) { tab ->
                            val maxTabContentWidth = when (tab) {
                                AppTab.Home -> 980.dp
                                AppTab.Search -> 940.dp
                                AppTab.Notifications -> 920.dp
                                AppTab.Profile -> 960.dp
                                AppTab.Settings -> 860.dp
                                AppTab.Xrpc -> 980.dp
                            }
                            ResponsiveContentFrame(
                                enabled = useWideRail,
                                maxWidth = maxTabContentWidth
                            ) {
                                when (tab) {
                                    AppTab.Home -> TimelineTab(
                                        sessionDid = state.session?.did,
                                        posts = state.timeline,
                                        isRefreshing = state.isBusy,
                                        isLoadingMore = state.isLoadingMoreTimeline,
                                        feedMode = homeFeedMode,
                                        customization = customization,
                                        onRefresh = onRefresh,
                                        onFeedModeChange = { homeFeedMode = it },
                                        onOpenComposer = onOpenComposer,
                                        onToggleLike = onToggleLike,
                                        onToggleRepost = onToggleRepost,
                                        onDeletePost = onDeletePost,
                                        onSelectProfile = onSelectProfile,
                                        onOpenPostThread = onOpenPostThread,
                                        onOpenMedia = onOpenMedia,
                                        onLoadMore = onLoadMoreTimeline
                                    )

                                    AppTab.Search -> SearchTab(
                                        query = state.searchQuery,
                                        isLoading = state.isSearchLoading,
                                        hasSearched = state.hasSearchExecuted,
                                        errorMessage = state.searchErrorMessage,
                                        actors = state.searchActors,
                                        posts = state.searchPosts,
                                        sessionDid = state.session?.did,
                                        customization = customization,
                                        onQueryChange = onSearchQueryChange,
                                        onSearch = onSearch,
                                        onSelectProfile = onSelectProfile,
                                        onToggleFollow = onToggleFollow,
                                        onToggleLike = onToggleLike,
                                        onToggleRepost = onToggleRepost,
                                        onDeletePost = onDeletePost,
                                        onOpenPostThread = onOpenPostThread,
                                        onOpenMedia = onOpenMedia
                                    )

                                    AppTab.Notifications -> NotificationTab(
                                        notifications = state.notifications,
                                        customization = customization,
                                        onSelectProfile = onSelectProfile
                                    )

                                    AppTab.Profile -> ProfileTab(
                                        profile = state.activeProfile,
                                        posts = state.activeProfileFeed,
                                        followers = state.followers,
                                        follows = state.follows,
                                        sessionDid = state.session?.did,
                                        customization = customization,
                                        onToggleFollow = onToggleFollow,
                                        onToggleLike = onToggleLike,
                                        onToggleRepost = onToggleRepost,
                                        onDeletePost = onDeletePost,
                                        onOpenPostThread = onOpenPostThread,
                                        onOpenMedia = onOpenMedia,
                                        onSelectProfile = onSelectProfile,
                                        onSelectProfileByHandle = onSelectProfileByHandle
                                    )

                                    AppTab.Settings -> SettingsTab(
                                        handle = state.session?.handle.orEmpty(),
                                        customization = customization,
                                        onMotionScaleChange = onMotionScaleChange,
                                        onCompactCardsChange = onCompactCardsChange,
                                        onExpressiveBackgroundChange = onExpressiveBackgroundChange,
                                        onQuickGesturesChange = onQuickGesturesChange,
                                        onLogout = onLogout
                                    )

                                    AppTab.Xrpc -> XrpcTab(
                                        method = state.rawHttpMethod,
                                        endpoint = state.rawEndpoint,
                                        paramsJson = state.rawParamsJson,
                                        bodyJson = state.rawBodyJson,
                                        result = state.rawResult,
                                        customization = customization,
                                        onMethodChanged = onRawMethodChanged,
                                        onEndpointChanged = onRawEndpointChanged,
                                        onParamsChanged = onRawParamsChanged,
                                        onBodyChanged = onRawBodyChanged,
                                        onRun = onRunRawXrpc
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponsiveContentFrame(
    enabled: Boolean,
    maxWidth: Dp,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    activeTab: AppTab,
    handle: String,
    onMenuClick: () -> Unit,
    onQuickNavigate: (AppTab) -> Unit
) {
    val settingsLabel = stringResource(R.string.tab_settings)
    val xrpcLabel = stringResource(R.string.tab_xrpc)
    val title = when (activeTab) {
        AppTab.Home -> ""
        AppTab.Search -> stringResource(R.string.tab_search)
        AppTab.Notifications -> stringResource(R.string.tab_notifications)
        AppTab.Profile -> stringResource(R.string.tab_profile)
        AppTab.Settings -> stringResource(R.string.tab_settings)
        AppTab.Xrpc -> stringResource(R.string.tab_xrpc)
    }

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.cd_menu))
            }
        },
        title = {
            if (activeTab == AppTab.Home) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = stringResource(R.string.cd_logo),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        actions = {
            if (activeTab == AppTab.Home) {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                AppBarRow(
                    maxItemCount = 2,
                    overflowIndicator = {
                        IconButton(onClick = { it.show() }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_menu)
                            )
                        }
                    }
                ) {
                    clickableItem(
                        onClick = { onQuickNavigate(AppTab.Settings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = settingsLabel
                    )
                    clickableItem(
                        onClick = { onQuickNavigate(AppTab.Xrpc) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                        label = xrpcLabel
                    )
                }
            } else {
                Text(
                    text = "@$handle",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    )
}

@Composable
private fun ExpressiveSidebar(
    activeTab: AppTab,
    profile: ActorUi?,
    handle: String,
    followersCount: Int,
    followsCount: Int,
    onNavigate: (AppTab) -> Unit
) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: handle.ifBlank { stringResource(R.string.guest) }
    val sideHandle = if (handle.isBlank()) "" else "@$handle"
    val menuItems = listOf(
        Triple(AppTab.Search, stringResource(R.string.tab_search), Icons.Default.Search),
        Triple(AppTab.Home, stringResource(R.string.tab_home), Icons.Default.Home),
        Triple(AppTab.Notifications, stringResource(R.string.tab_notifications), Icons.Default.Notifications),
        Triple(AppTab.Profile, stringResource(R.string.tab_profile), Icons.Default.Person),
        Triple(AppTab.Settings, stringResource(R.string.tab_settings), Icons.Default.Settings),
        Triple(AppTab.Xrpc, stringResource(R.string.tab_xrpc), Icons.Default.Explore)
    )

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerTonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(AppTab.Profile) }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Avatar(url = profile?.avatar, size = 62.dp)
            Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            if (sideHandle.isNotBlank()) {
                Text(sideHandle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = stringResource(R.string.sidebar_follow_stats, followersCount, followsCount),
                fontWeight = FontWeight.SemiBold
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(menuItems) { (tab, label, icon) ->
                NavigationDrawerItem(
                    label = { Text(label) },
                    selected = activeTab == tab,
                    onClick = { onNavigate(tab) },
                    icon = { Icon(icon, contentDescription = label) },
                    shape = RoundedCornerShape(18.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedContainerColor = Color.Transparent
                    )
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.feedback)) },
                leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) }
            )
            OutlinedTextField(
                value = stringResource(R.string.help),
                onValueChange = {},
                enabled = false,
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTab(
    sessionDid: String?,
    posts: List<PostUi>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    feedMode: HomeFeedMode,
    customization: UiCustomization,
    onRefresh: () -> Unit,
    onFeedModeChange: (HomeFeedMode) -> Unit,
    onOpenComposer: () -> Unit,
    onToggleLike: (PostUi) -> Unit,
    onToggleRepost: (PostUi) -> Unit,
    onDeletePost: (PostUi) -> Unit,
    onSelectProfile: (ActorUi) -> Unit,
    onOpenPostThread: (PostUi) -> Unit,
    onOpenMedia: (PostMediaUi) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    var selectedTrend by rememberSaveable { mutableStateOf("OpenAI") }
    val trendTopics = listOf("Israel-Iran War", "OpenAI", "B-Movies", "Dateline", "Music")
    val feedPosts = posts

    LaunchedEffect(listState.firstVisibleItemIndex, feedPosts.size) {
        if (feedPosts.isNotEmpty() && listState.firstVisibleItemIndex >= feedPosts.lastIndex - 3) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicator = {}
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HomeFeedHeader(
                    selectedMode = feedMode,
                    selectedTrend = selectedTrend,
                    trends = trendTopics,
                    onModeChange = onFeedModeChange,
                    onTrendChange = { selectedTrend = it },
                    onOpenComposer = onOpenComposer
                )
            }
            items(feedPosts, key = { it.uri }) { post ->
                PostCard(
                    post = post,
                    canDelete = sessionDid == post.author.did,
                    customization = customization,
                    onLike = { onToggleLike(post) },
                    onRepost = { onToggleRepost(post) },
                    onDelete = { onDeletePost(post) },
                    onSelectProfile = { onSelectProfile(post.author) },
                    onOpenThread = { onOpenPostThread(post) },
                    onOpenMedia = onOpenMedia
                )
            }
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveCircularLoadingIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExpressiveLoadingStrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier.size(28.dp),
            indicatorColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExpressiveCircularLoadingIndicator(
    modifier: Modifier = Modifier
) {
    LoadingIndicator(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun HomeFeedHeader(
    selectedMode: HomeFeedMode,
    selectedTrend: String,
    trends: List<String>,
    onModeChange: (HomeFeedMode) -> Unit,
    onTrendChange: (String) -> Unit,
    onOpenComposer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabRow(selectedTabIndex = selectedMode.ordinal) {
            HomeFeedMode.entries.forEach { mode ->
                Tab(
                    selected = selectedMode == mode,
                    onClick = { onModeChange(mode) },
                    text = {
                        Text(
                            when (mode) {
                                HomeFeedMode.Discover -> stringResource(R.string.feed_mode_discover)
                                HomeFeedMode.Following -> stringResource(R.string.feed_mode_following)
                            },
                            fontWeight = if (mode == selectedMode) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(trends) { topic ->
                FilterChip(
                    selected = selectedTrend == topic,
                    onClick = { onTrendChange(topic) },
                    label = { Text(topic) }
                )
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clickable { onOpenComposer() },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(url = null, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_status_prompt),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onOpenComposer) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
                IconButton(onClick = onOpenComposer) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTab(
    query: String,
    isLoading: Boolean,
    hasSearched: Boolean,
    errorMessage: String?,
    actors: List<ActorUi>,
    posts: List<PostUi>,
    sessionDid: String?,
    customization: UiCustomization,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectProfile: (ActorUi) -> Unit,
    onToggleFollow: (ActorUi) -> Unit,
    onToggleLike: (PostUi) -> Unit,
    onToggleRepost: (PostUi) -> Unit,
    onDeletePost: (PostUi) -> Unit,
    onOpenPostThread: (PostUi) -> Unit,
    onOpenMedia: (PostMediaUi) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(SearchResultTab.Users) }
    var isSearchBarActive by rememberSaveable { mutableStateOf(false) }
    val hasResults = when (selectedTab) {
        SearchResultTab.Users -> actors.isNotEmpty()
        SearchResultTab.Posts -> posts.isNotEmpty()
    }
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
        item {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = {
                            isSearchBarActive = false
                            onSearch()
                        },
                        expanded = isSearchBarActive,
                        onExpandedChange = { isSearchBarActive = it },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_search)
                            )
                        },
                        trailingIcon = {
                            Row {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear))
                                    }
                                }
                                IconButton(onClick = {
                                    isSearchBarActive = false
                                    onSearch()
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                                }
                            }
                        }
                    )
                },
                expanded = isSearchBarActive,
                onExpandedChange = { isSearchBarActive = it },
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {}
        }
        if (query.isNotBlank()) {
            item {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    SearchResultTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = when (tab) {
                                        SearchResultTab.Users -> stringResource(R.string.search_users)
                                        SearchResultTab.Posts -> stringResource(R.string.search_posts)
                                    },
                                    fontWeight = if (tab == selectedTab) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }
        }
        if (query.isBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (isLoading) {
            item {
                ExpressiveLoadingStrip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }
        if (!errorMessage.isNullOrBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        if (hasSearched && query.isNotBlank() && !isLoading && errorMessage.isNullOrBlank() && !hasResults) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (selectedTab) {
            SearchResultTab.Users -> {
                items(actors, key = { it.did }) { actor ->
                    ActorRow(
                        actor = actor,
                        customization = customization,
                        onSelect = { onSelectProfile(actor) },
                        onToggleFollow = { onToggleFollow(actor) }
                    )
                }
            }
            SearchResultTab.Posts -> {
                items(posts, key = { it.uri }) { post ->
                    PostCard(
                        post = post,
                        canDelete = sessionDid == post.author.did,
                        customization = customization,
                        onLike = { onToggleLike(post) },
                        onRepost = { onToggleRepost(post) },
                        onDelete = { onDeletePost(post) },
                        onSelectProfile = { onSelectProfile(post.author) },
                        onOpenThread = { onOpenPostThread(post) },
                        onOpenMedia = onOpenMedia
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationTab(
    notifications: List<NotificationUi>,
    customization: UiCustomization,
    onSelectProfile: (ActorUi) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ExpressiveBanner(
                title = stringResource(R.string.notifications_title),
                subtitle = stringResource(R.string.notifications_subtitle),
                metric = stringResource(R.string.notifications_metric, notifications.size)
            )
        }
        items(notifications, key = { it.uri }) { item ->
            ElevatedCard(
                shape = RoundedCornerShape(if (customization.compactCards) 18.dp else 24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (item.isRead) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(url = item.author.avatar, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectProfile(item.author) }
                        ) {
                            Text(item.author.displayName, fontWeight = FontWeight.Bold)
                            Text(
                                "@${item.author.handle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            item.reason,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.text.isNotBlank()) {
                        Text(item.text)
                    }
                    Text(
                        item.indexedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(
    profile: ActorUi?,
    posts: List<PostUi>,
    followers: List<ActorUi>,
    follows: List<ActorUi>,
    sessionDid: String?,
    customization: UiCustomization,
    onToggleFollow: (ActorUi) -> Unit,
    onToggleLike: (PostUi) -> Unit,
    onToggleRepost: (PostUi) -> Unit,
    onDeletePost: (PostUi) -> Unit,
    onOpenPostThread: (PostUi) -> Unit,
    onOpenMedia: (PostMediaUi) -> Unit,
    onSelectProfile: (ActorUi) -> Unit,
    onSelectProfileByHandle: (String) -> Unit
) {
    var targetHandle by rememberSaveable(profile?.did) { mutableStateOf("") }
    var profileSection by rememberSaveable(profile?.did) { mutableStateOf(ProfileSection.Posts) }
    var relationSheet by rememberSaveable(profile?.did) { mutableStateOf<ProfileRelationSheet?>(null) }

    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.profile_loading))
        }
        return
    }

    val sectionPosts = when (profileSection) {
        ProfileSection.Posts -> posts
        ProfileSection.Replies -> posts.take(5)
        ProfileSection.Media -> posts.filter { it.media.isNotEmpty() }.ifEmpty { posts.take(3) }
        ProfileSection.Likes -> posts.sortedByDescending { it.likeCount }.take(8)
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProfileHeader(
                profile = profile,
                sessionDid = sessionDid,
                postCount = posts.size,
                followerCount = followers.size,
                followingCount = follows.size,
                onToggleFollow = { onToggleFollow(profile) }
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetHandle,
                        onValueChange = { targetHandle = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.profile_handle_label)) },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            onSelectProfileByHandle(targetHandle)
                            targetHandle = ""
                        },
                        enabled = targetHandle.isNotBlank()
                    ) {
                        Text(stringResource(R.string.open_button))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { relationSheet = ProfileRelationSheet.Followers },
                        label = { Text(stringResource(R.string.followers_count_label, followers.size)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                    AssistChip(
                        onClick = { relationSheet = ProfileRelationSheet.Follows },
                        label = { Text(stringResource(R.string.follows_count_label, follows.size)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            }
        }
        item {
            TabRow(
                selectedTabIndex = profileSection.ordinal,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                ProfileSection.entries.forEach { section ->
                    Tab(
                        selected = section == profileSection,
                        onClick = { profileSection = section },
                        text = {
                            Text(
                                text = when (section) {
                                    ProfileSection.Posts -> stringResource(R.string.profile_section_posts)
                                    ProfileSection.Replies -> stringResource(R.string.profile_section_replies)
                                    ProfileSection.Media -> stringResource(R.string.profile_section_media)
                                    ProfileSection.Likes -> stringResource(R.string.profile_section_likes)
                                },
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
        if (sectionPosts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = stringResource(R.string.profile_empty_section),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(sectionPosts, key = { it.uri }) { post ->
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    PostCard(
                        post = post,
                        canDelete = sessionDid == post.author.did,
                        customization = customization,
                        onLike = { onToggleLike(post) },
                        onRepost = { onToggleRepost(post) },
                        onDelete = { onDeletePost(post) },
                        onSelectProfile = { onSelectProfile(post.author) },
                        onOpenThread = { onOpenPostThread(post) },
                        onOpenMedia = onOpenMedia
                    )
                }
            }
        }
    }

    if (relationSheet != null) {
        val currentRelationSheet = relationSheet!!
        val sheetActors = when (currentRelationSheet) {
            ProfileRelationSheet.Followers -> followers
            ProfileRelationSheet.Follows -> follows
        }
        ModalBottomSheet(
            onDismissRequest = { relationSheet = null },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = when (currentRelationSheet) {
                            ProfileRelationSheet.Followers -> stringResource(R.string.profile_followers)
                            ProfileRelationSheet.Follows -> stringResource(R.string.profile_following_count, follows.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (sheetActors.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_results),
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(sheetActors, key = { it.did }) { actor ->
                        ActorRow(
                            actor = actor,
                            customization = customization,
                            onSelect = {
                                relationSheet = null
                                onSelectProfile(actor)
                            },
                            onToggleFollow = { onToggleFollow(actor) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: ActorUi,
    sessionDid: String?,
    postCount: Int,
    followerCount: Int,
    followingCount: Int,
    onToggleFollow: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (profile.banner.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            )
                    )
                } else {
                    AsyncImage(
                        model = profile.banner,
                        contentDescription = stringResource(R.string.cd_profile_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(url = profile.avatar, size = 78.dp)
                    Spacer(Modifier.weight(1f))
                    if (sessionDid != profile.did) {
                        Button(onClick = onToggleFollow) {
                            Text(
                                if (profile.viewerFollowingUri == null) {
                                    stringResource(R.string.follow)
                                } else {
                                    stringResource(R.string.unfollow)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "@${profile.handle}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.profile_followers_count, followerCount), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.profile_following_count, followingCount), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.profile_posts_count, postCount), fontWeight = FontWeight.SemiBold)
                }
                if (!profile.description.isNullOrBlank()) {
                    Text(
                        text = profile.description,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    handle: String,
    customization: UiCustomization,
    onMotionScaleChange: (Float) -> Unit,
    onCompactCardsChange: (Boolean) -> Unit,
    onExpressiveBackgroundChange: (Float) -> Unit,
    onQuickGesturesChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ExpressiveBanner(
                title = stringResource(R.string.settings_title),
                subtitle = "@$handle",
                metric = stringResource(R.string.settings_metric)
            )
        }
        item {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.logout_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.logout_button))
                    }
                }
            }
        }
        item {
            CustomizationSection(
                customization = customization,
                onMotionScaleChange = onMotionScaleChange,
                onCompactCardsChange = onCompactCardsChange,
                onExpressiveBackgroundChange = onExpressiveBackgroundChange,
                onQuickGesturesChange = onQuickGesturesChange
            )
        }
    }
}

@Composable
private fun XrpcTab(
    method: RawHttpMethod,
    endpoint: String,
    paramsJson: String,
    bodyJson: String,
    result: String,
    customization: UiCustomization,
    onMethodChanged: (RawHttpMethod) -> Unit,
    onEndpointChanged: (String) -> Unit,
    onParamsChanged: (String) -> Unit,
    onBodyChanged: (String) -> Unit,
    onRun: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ExpressiveBanner(
                title = stringResource(R.string.xrpc_title),
                subtitle = stringResource(R.string.xrpc_subtitle),
                metric = method.name
            )
        }
        item {
            OutlinedTextField(
                value = endpoint,
                onValueChange = onEndpointChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.xrpc_method_label)) }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = method == RawHttpMethod.GET,
                    onClick = { onMethodChanged(RawHttpMethod.GET) },
                    label = { Text(stringResource(R.string.http_get)) }
                )
                FilterChip(
                    selected = method == RawHttpMethod.POST,
                    onClick = { onMethodChanged(RawHttpMethod.POST) },
                    label = { Text(stringResource(R.string.http_post)) }
                )
            }
        }
        item {
            OutlinedTextField(
                value = paramsJson,
                onValueChange = onParamsChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.xrpc_get_params_label)) },
                minLines = 4
            )
        }
        item {
            OutlinedTextField(
                value = bodyJson,
                onValueChange = onBodyChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.xrpc_post_body_label)) },
                minLines = 6
            )
        }
        item {
            Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.execute))
            }
        }
        if (result.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(if (customization.compactCards) 14.dp else 18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveBanner(
    title: String,
    subtitle: String,
    metric: String
) {
    ElevatedCard(
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 20.dp, bottomEnd = 34.dp, bottomStart = 20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            AssistChip(
                onClick = {},
                label = { Text(metric) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
private fun CustomizationSection(
    customization: UiCustomization,
    onMotionScaleChange: (Float) -> Unit,
    onCompactCardsChange: (Boolean) -> Unit,
    onExpressiveBackgroundChange: (Float) -> Unit,
    onQuickGesturesChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.padding(horizontal = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 12.dp, end = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.customization), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.animation_speed), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = customization.motionScale,
                onValueChange = onMotionScaleChange,
                valueRange = 0.6f..1.6f
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.compact_cards), modifier = Modifier.weight(1f))
                Switch(checked = customization.compactCards, onCheckedChange = onCompactCardsChange)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.quick_gestures), modifier = Modifier.weight(1f))
                Switch(checked = customization.quickGestures, onCheckedChange = onQuickGesturesChange)
            }
            Text(stringResource(R.string.expressive_background), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = customization.expressiveBackground,
                onValueChange = onExpressiveBackgroundChange,
                valueRange = 0.35f..1f
            )
        }
    }
}

@Composable
private fun ActorRow(
    actor: ActorUi,
    customization: UiCustomization,
    onSelect: () -> Unit,
    onToggleFollow: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(if (customization.compactCards) 16.dp else 22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSelect),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(url = actor.avatar, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(actor.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        "@${actor.handle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onToggleFollow) {
                Text(
                    if (actor.viewerFollowingUri.isNullOrBlank()) {
                        stringResource(R.string.follow)
                    } else {
                        stringResource(R.string.unfollow)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostCard(
    post: PostUi,
    canDelete: Boolean,
    customization: UiCustomization,
    onLike: () -> Unit,
    onRepost: () -> Unit,
    onDelete: () -> Unit,
    onSelectProfile: () -> Unit,
    onOpenThread: () -> Unit,
    onOpenMedia: (PostMediaUi) -> Unit
) {
    val gestureInteractionSource = remember { MutableInteractionSource() }
    val pressed = gestureInteractionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = 420f / customization.motionScale.coerceAtLeast(0.35f)
        ),
        label = "post-card-scale"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value
            ),
        shape = RoundedCornerShape(
            topStart = if (customization.compactCards) 18.dp else 24.dp,
            topEnd = if (customization.compactCards) 18.dp else 24.dp,
            bottomEnd = if (customization.compactCards) 14.dp else 20.dp,
            bottomStart = if (customization.compactCards) 14.dp else 20.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clickable(onClick = onSelectProfile)) {
                    Avatar(url = post.author.avatar, size = 38.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.author.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        "@${post.author.handle} ・ ${post.indexedAt.take(16)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_delete))
                    }
                }
            }
            val bodyModifier = if (customization.quickGestures) {
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = gestureInteractionSource,
                        indication = null,
                        onClick = {},
                        onLongClick = onRepost,
                        onDoubleClick = onLike
                    )
            } else {
                Modifier.fillMaxWidth()
            }
            if (post.text.isNotBlank()) {
                Box(modifier = bodyModifier.padding(vertical = 2.dp)) {
                    LinkifiedText(
                        text = post.text,
                        style = MaterialTheme.typography.bodyLarge,
                        onNonLinkClick = onOpenThread
                    )
                }
            }
            if (post.media.isNotEmpty()) {
                PostMediaGallery(
                    media = post.media,
                    onMediaClick = onOpenMedia
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.reply_count, post.replyCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRepost) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.cd_repost),
                            tint = if (post.viewerRepostUri == null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    Text(post.repostCount.toString())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (post.viewerLikeUri == null) {
                                Icons.Default.FavoriteBorder
                            } else {
                                Icons.Default.Favorite
                            },
                            contentDescription = stringResource(R.string.cd_like),
                            tint = if (post.viewerLikeUri == null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Text(post.likeCount.toString())
                }
            }
            if (customization.quickGestures) {
                Text(
                    text = stringResource(R.string.gesture_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PostMediaGallery(
    media: List<PostMediaUi>,
    onMediaClick: (PostMediaUi) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        media.forEach { item ->
            val previewHeight = if (item.type == PostMediaType.Video) 190.dp else 230.dp
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onMediaClick(item)
                    }
            ) {
                Box {
                    if (item.type == PostMediaType.Video) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.alt.ifBlank { null },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewHeight)
                        )
                    } else {
                        AsyncImage(
                            model = item.thumbnailUrl ?: item.url,
                            contentDescription = item.alt.ifBlank { null },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewHeight)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomStart = 12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
                    ) {
                        Text(
                            text = if (item.type == PostMediaType.Video) {
                                stringResource(R.string.video_post)
                            } else {
                                stringResource(R.string.image_post)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineVideoPlayer(
    uri: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                player = exoPlayer
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        }
    )
}

@Composable
private fun MediaViewerFullscreen(
    media: PostMediaUi,
    onDismiss: () -> Unit
) {
    var scale by remember(media.url) { mutableFloatStateOf(1f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToZoom(
                        scale = scale,
                        onScaleChange = { scale = it }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (media.type == PostMediaType.Video) {
                    InlineVideoPlayer(
                        uri = media.url,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale
                            )
                    )
                } else {
                    AsyncImage(
                        model = media.url,
                        contentDescription = media.alt.ifBlank { null },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale
                            )
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White
                )
            }
        }
    }
}

private fun Modifier.pinchToZoom(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    minScale: Float = 1f,
    maxScale: Float = 4f
): Modifier {
    return this.pointerInput(scale, minScale, maxScale) {
        awaitEachGesture {
            do {
                val event = awaitPointerEvent()
                val activePointerCount = event.changes.count { it.pressed }
                if (activePointerCount > 1) {
                    val zoomChange = event.calculateZoom()
                    if (zoomChange != 1f) {
                        onScaleChange((scale * zoomChange).coerceIn(minScale, maxScale))
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun Avatar(url: String?, size: Dp) {
    if (url.isNullOrBlank()) {
        Surface(
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {}
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun ComposerMediaPreview(media: ComposerMediaUi) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = media.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (media.type == ComposerMediaType.Image) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = stringResource(R.string.image_post),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = stringResource(R.string.video_selected_hint),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostThreadSheet(
    rootPost: PostUi?,
    replies: List<PostUi>,
    isLoading: Boolean,
    postTranslations: Map<String, PostTranslationState>,
    onDismiss: () -> Unit,
    onToggleLike: (PostUi) -> Unit,
    onToggleRepost: (PostUi) -> Unit,
    onDeletePost: (PostUi) -> Unit,
    onPreparePostTranslation: (PostUi) -> Unit,
    onTranslatePost: (PostUi) -> Unit,
    onOpenMedia: (PostMediaUi) -> Unit
) {
    val root = rootPost ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.post_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                ThreadPostItem(
                    post = root,
                    isRoot = true,
                    translationState = postTranslations[root.uri],
                    onToggleLike = { onToggleLike(root) },
                    onToggleRepost = { onToggleRepost(root) },
                    onDeletePost = { onDeletePost(root) },
                    onPreparePostTranslation = { onPreparePostTranslation(root) },
                    onTranslatePost = { onTranslatePost(root) },
                    onOpenMedia = onOpenMedia
                )
            }
            item {
                Text(
                    text = stringResource(R.string.replies_count, replies.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveCircularLoadingIndicator(
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            if (!isLoading && replies.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = stringResource(R.string.no_replies),
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(replies, key = { it.uri }) { reply ->
                    ThreadPostItem(
                        post = reply,
                        isRoot = false,
                        translationState = postTranslations[reply.uri],
                        onToggleLike = { onToggleLike(reply) },
                        onToggleRepost = { onToggleRepost(reply) },
                        onDeletePost = { onDeletePost(reply) },
                        onPreparePostTranslation = { onPreparePostTranslation(reply) },
                        onTranslatePost = { onTranslatePost(reply) },
                        onOpenMedia = onOpenMedia
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadPostItem(
    post: PostUi,
    isRoot: Boolean,
    translationState: PostTranslationState?,
    onToggleLike: () -> Unit,
    onToggleRepost: () -> Unit,
    onDeletePost: () -> Unit,
    onPreparePostTranslation: () -> Unit,
    onTranslatePost: () -> Unit,
    onOpenMedia: (PostMediaUi) -> Unit
) {
    LaunchedEffect(post.uri) {
        onPreparePostTranslation()
    }

    Surface(
        shape = RoundedCornerShape(if (isRoot) 20.dp else 16.dp),
        color = if (isRoot) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = post.author.avatar, size = if (isRoot) 40.dp else 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.author.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        text = "@${post.author.handle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isRoot) {
                    IconButton(onClick = onDeletePost) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_delete))
                    }
                }
            }
            LinkifiedText(
                text = post.text,
                style = if (isRoot) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
            )
            if (translationState?.canTranslate == true) {
                TextButton(
                    onClick = onTranslatePost,
                    enabled = !translationState.isTranslating
                ) {
                    Text(
                        text = if (translationState.isTranslating) {
                            stringResource(R.string.translating)
                        } else {
                            stringResource(R.string.translate_post)
                        }
                    )
                }
            }
            val translatedText = translationState?.translatedText
            if (!translatedText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.translated_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = translatedText,
                            style = if (isRoot) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            if (!translationState?.errorMessage.isNullOrBlank()) {
                Text(
                    text = translationState?.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (post.media.isNotEmpty()) {
                PostMediaGallery(
                    media = post.media,
                    onMediaClick = onOpenMedia
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleRepost) {
                    Text(stringResource(R.string.repost_count, post.repostCount))
                }
                TextButton(onClick = onToggleLike) {
                    Text(stringResource(R.string.like_count, post.likeCount))
                }
                if (!isRoot) {
                    Text(
                        text = post.indexedAt.take(16),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(
    value: String,
    composerMedia: ComposerMediaUi?,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    onPickMedia: () -> Unit,
    onClearMedia: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val remaining = maxLength - value.length
    val isOverLimit = remaining < 0
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.new_post),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                placeholder = { Text(stringResource(R.string.composer_placeholder)) },
                shape = RoundedCornerShape(22.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onPickMedia) {
                    Text(stringResource(R.string.attach_media))
                }
                if (composerMedia != null) {
                    TextButton(onClick = onClearMedia) {
                        Text(stringResource(R.string.remove_media))
                    }
                }
            }
            if (composerMedia != null) {
                ComposerMediaPreview(composerMedia)
            }
            Text(
                text = stringResource(R.string.remaining_chars, remaining),
                style = MaterialTheme.typography.labelMedium,
                color = if (isOverLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSubmit,
                    enabled = (value.isNotBlank() || composerMedia != null) && !isOverLimit
                ) {
                    Text(stringResource(R.string.post_button))
                }
            }
        }
    }
}
