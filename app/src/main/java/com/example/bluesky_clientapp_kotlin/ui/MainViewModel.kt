package com.example.bluesky_clientapp_kotlin.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluesky_clientapp_kotlin.R
import com.example.bluesky_clientapp_kotlin.data.ml.OnDeviceTranslationService
import com.example.bluesky_clientapp_kotlin.data.model.ActorUi
import com.example.bluesky_clientapp_kotlin.data.model.AuthSession
import com.example.bluesky_clientapp_kotlin.data.model.DraftMediaAttachment
import com.example.bluesky_clientapp_kotlin.data.model.DraftMediaType
import com.example.bluesky_clientapp_kotlin.data.model.NotificationUi
import com.example.bluesky_clientapp_kotlin.data.model.PostUi
import com.example.bluesky_clientapp_kotlin.data.network.ATProtoKitClient
import com.example.bluesky_clientapp_kotlin.data.network.BlueskyOAuthConfig
import com.example.bluesky_clientapp_kotlin.data.network.LoopbackOAuthCallbackServer
import com.example.bluesky_clientapp_kotlin.data.network.OAuthAuthorizationRequest
import com.example.bluesky_clientapp_kotlin.data.repository.BlueskyRepository
import com.example.bluesky_clientapp_kotlin.data.store.SessionStore
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class AppTab {
    Home,
    Search,
    Notifications,
    Profile,
    Settings,
    Xrpc
}

enum class RawHttpMethod {
    GET,
    POST
}

enum class ComposerMediaType {
    Image,
    Video
}

data class ComposerMediaUi(
    val uri: String,
    val mimeType: String,
    val displayName: String,
    val type: ComposerMediaType
)

data class PostTranslationState(
    val isDetectingLanguage: Boolean = false,
    val detectedLanguageTag: String? = null,
    val canTranslate: Boolean = false,
    val isTranslating: Boolean = false,
    val translatedText: String? = null,
    val errorMessage: String? = null
)

data class UiState(
    val isSessionRestoring: Boolean = false,
    val isBusy: Boolean = false,
    val oauthLoginUrl: String? = null,
    val session: AuthSession? = null,
    val activeTab: AppTab = AppTab.Home,
    val timeline: List<PostUi> = emptyList(),
    val timelineCursor: String? = null,
    val isLoadingMoreTimeline: Boolean = false,
    val notifications: List<NotificationUi> = emptyList(),
    val searchQuery: String = "",
    val searchPosts: List<PostUi> = emptyList(),
    val searchActors: List<ActorUi> = emptyList(),
    val isSearchLoading: Boolean = false,
    val hasSearchExecuted: Boolean = false,
    val searchErrorMessage: String? = null,
    val selfProfile: ActorUi? = null,
    val selfProfileFeed: List<PostUi> = emptyList(),
    val selfFollowers: List<ActorUi> = emptyList(),
    val selfFollows: List<ActorUi> = emptyList(),
    val activeProfile: ActorUi? = null,
    val activeProfileFeed: List<PostUi> = emptyList(),
    val followers: List<ActorUi> = emptyList(),
    val follows: List<ActorUi> = emptyList(),
    val composerOpen: Boolean = false,
    val composerText: String = "",
    val composerMedia: ComposerMediaUi? = null,
    val selectedPostThreadRoot: PostUi? = null,
    val selectedPostThreadReplies: List<PostUi> = emptyList(),
    val isPostThreadLoading: Boolean = false,
    val appLanguageTag: String = Locale.getDefault().toLanguageTag(),
    val postTranslations: Map<String, PostTranslationState> = emptyMap(),
    val rawHttpMethod: RawHttpMethod = RawHttpMethod.GET,
    val rawEndpoint: String = "app.bsky.feed.getTimeline",
    val rawParamsJson: String = "{\n  \"limit\": \"20\"\n}",
    val rawBodyJson: String = "{\n}\n",
    val rawResult: String = "",
    val message: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BlueskyRepository(
        client = ATProtoKitClient(),
        sessionStore = SessionStore(application)
    )
    private val translationService = OnDeviceTranslationService()

    var state by mutableStateOf(UiState(isSessionRestoring = true))
        private set
    private var searchJob: Job? = null

    init {
        state = state.copy(appLanguageTag = getCurrentAppLanguageTag())
        viewModelScope.launch {
            restoreSession()
        }
        viewModelScope.launch {
            oauthResultChannel.receiveAsFlow().collect { result ->
                result.onSuccess { callbackUri ->
                    handleOAuthRedirect(callbackUri)
                }.onFailure { error ->
                    clearPendingOAuthRequest()
                    val message = if (error is TimeoutCancellationException) {
                        stringRes(R.string.msg_oauth_cancelled)
                    } else {
                        error.message ?: stringRes(R.string.msg_oauth_cancelled)
                    }
                    state = state.copy(isBusy = false, message = message)
                }
            }
        }
    }

    override fun onCleared() {
        translationService.close()
        repository.close()
        super.onCleared()
    }

    fun consumeOAuthLoginUrl() {
        state = state.copy(oauthLoginUrl = null)
    }

    fun onOAuthBrowserLaunchFailed() {
        clearPendingOAuthRequest()
        state = state.copy(
            oauthLoginUrl = null,
            isBusy = false,
            message = stringRes(R.string.msg_oauth_browser_launch_failed)
        )
    }

    fun updateSearchQuery(value: String) {
        state = state.copy(searchQuery = value)
        val query = value.trim()
        if (query.isBlank()) {
            searchJob?.cancel()
            state = state.copy(
                searchPosts = emptyList(),
                searchActors = emptyList(),
                isSearchLoading = false,
                hasSearchExecuted = false,
                searchErrorMessage = null
            )
            return
        }
        queueSearch(query = query, immediate = false)
    }

    fun updateComposerText(value: String) {
        state = state.copy(composerText = value)
    }

    fun setComposerMedia(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val mimeType = resolver.getType(uri)
        if (mimeType.isNullOrBlank()) {
            state = state.copy(message = stringRes(R.string.msg_unsupported_media_type))
            return
        }
        val mediaType = when {
            mimeType.startsWith("image/") -> ComposerMediaType.Image
            mimeType.startsWith("video/") -> ComposerMediaType.Video
            else -> {
                state = state.copy(message = stringRes(R.string.msg_unsupported_media_type))
                return
            }
        }
        val displayName = resolveDisplayName(resolver, uri)
        state = state.copy(
            composerMedia = ComposerMediaUi(
                uri = uri.toString(),
                mimeType = mimeType,
                displayName = displayName,
                type = mediaType
            )
        )
    }

    fun clearComposerMedia() {
        state = state.copy(composerMedia = null)
    }

    fun updateRawEndpoint(value: String) {
        state = state.copy(rawEndpoint = value)
    }

    fun updateRawParams(value: String) {
        state = state.copy(rawParamsJson = value)
    }

    fun updateRawBody(value: String) {
        state = state.copy(rawBodyJson = value)
    }

    fun updateRawMethod(value: RawHttpMethod) {
        state = state.copy(rawHttpMethod = value)
    }

    fun openComposer() {
        state = state.copy(composerOpen = true)
    }

    fun closeComposer() {
        state = state.copy(composerOpen = false)
    }

    fun openPostThread(post: PostUi) {
        viewModelScope.launch {
            state = state.copy(
                selectedPostThreadRoot = post,
                selectedPostThreadReplies = emptyList(),
                isPostThreadLoading = true,
                postTranslations = emptyMap(),
                appLanguageTag = getCurrentAppLanguageTag()
            )
            runCatching {
                repository.getPostThread(post.uri)
            }.onSuccess { thread ->
                state = state.copy(
                    selectedPostThreadRoot = thread.root,
                    selectedPostThreadReplies = thread.replies,
                    isPostThreadLoading = false
                )
            }.onFailure { error ->
                state = state.copy(isPostThreadLoading = false)
                if (!handleAuthFailure(error)) {
                    state = state.copy(message = error.message ?: stringRes(R.string.msg_fetch_thread_failed))
                }
            }
        }
    }

    fun closePostThread() {
        state = state.copy(
            selectedPostThreadRoot = null,
            selectedPostThreadReplies = emptyList(),
            isPostThreadLoading = false,
            postTranslations = emptyMap()
        )
    }

    fun preparePostTranslation(post: PostUi) {
        if (post.text.isBlank()) return
        val current = state.postTranslations[post.uri]
        if (current?.isDetectingLanguage == true) return
        if (current?.detectedLanguageTag != null) return

        updatePostTranslation(post.uri) {
            it.copy(isDetectingLanguage = true, errorMessage = null)
        }

        viewModelScope.launch {
            val appLanguageTag = getCurrentAppLanguageTag()
            state = state.copy(appLanguageTag = appLanguageTag)

            val detectedLanguageTag = runCatching {
                translationService.identifyLanguageTag(post.text)
            }.getOrNull()

            val canTranslate = detectedLanguageTag?.let { sourceTag ->
                canTranslateBetween(sourceTag, appLanguageTag)
            } ?: false

            updatePostTranslation(post.uri) {
                it.copy(
                    isDetectingLanguage = false,
                    detectedLanguageTag = detectedLanguageTag,
                    canTranslate = canTranslate,
                    errorMessage = null
                )
            }
        }
    }

    fun translatePostToAppLanguage(post: PostUi) {
        if (post.text.isBlank()) return
        val appLanguageTag = getCurrentAppLanguageTag()
        state = state.copy(appLanguageTag = appLanguageTag)

        viewModelScope.launch {
            val current = state.postTranslations[post.uri]
            if (current?.isTranslating == true) return@launch

            val detectedLanguageTag = current?.detectedLanguageTag ?: runCatching {
                translationService.identifyLanguageTag(post.text)
            }.getOrNull()

            if (detectedLanguageTag == null || !canTranslateBetween(detectedLanguageTag, appLanguageTag)) {
                updatePostTranslation(post.uri) {
                    it.copy(
                        isDetectingLanguage = false,
                        detectedLanguageTag = detectedLanguageTag,
                        canTranslate = false
                    )
                }
                return@launch
            }

            updatePostTranslation(post.uri) {
                it.copy(
                    detectedLanguageTag = detectedLanguageTag,
                    canTranslate = true,
                    isTranslating = true,
                    errorMessage = null
                )
            }

            runCatching {
                translationService.translate(
                    text = post.text,
                    sourceLanguageTag = detectedLanguageTag,
                    targetLanguageTag = appLanguageTag
                )
            }.onSuccess { translated ->
                updatePostTranslation(post.uri) {
                    it.copy(
                        isTranslating = false,
                        translatedText = translated
                    )
                }
            }.onFailure {
                updatePostTranslation(post.uri) {
                    it.copy(
                        isTranslating = false,
                        errorMessage = stringRes(R.string.msg_translation_failed)
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    fun setTab(tab: AppTab) {
        if (tab == AppTab.Profile) {
            state = state.copy(
                activeTab = AppTab.Profile,
                activeProfile = state.selfProfile ?: state.activeProfile,
                activeProfileFeed = if (state.selfProfileFeed.isNotEmpty()) {
                    state.selfProfileFeed
                } else {
                    state.activeProfileFeed
                },
                followers = if (state.selfFollowers.isNotEmpty()) state.selfFollowers else state.followers,
                follows = if (state.selfFollows.isNotEmpty()) state.selfFollows else state.follows
            )
            return
        }
        state = state.copy(activeTab = tab)
    }

    fun login() {
        viewModelScope.launch {
            runBusy {
                clearPendingOAuthRequest()
                state = state.copy(
                    oauthLoginUrl = null,
                    message = null
                )
                val request = startOAuthFlow(
                    createRequest = { redirectUri ->
                        repository.createOAuthAuthorizationRequest(redirectUri = redirectUri)
                    }
                )
                state = state.copy(
                    oauthLoginUrl = request.authorizationUrl,
                    message = stringRes(R.string.msg_opening_oauth)
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runBusy {
                clearPendingOAuthRequest()
                repository.logout()
                state = UiState(
                    isSessionRestoring = false,
                    message = stringRes(R.string.msg_logged_out)
                )
            }
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            runBusy {
                refreshTimelineWithNewest()
            }
        }
    }

    fun loadMoreTimeline() {
        if (state.isLoadingMoreTimeline) return
        viewModelScope.launch {
            val cursor = state.timelineCursor ?: return@launch
            state = state.copy(isLoadingMoreTimeline = true)
            try {
                val page = repository.getTimeline(limit = 40, cursor = cursor)
                val merged = (state.timeline + page.posts).distinctBy { it.uri }
                state = state.copy(
                    timeline = merged,
                    timelineCursor = page.cursor,
                    isLoadingMoreTimeline = false
                )
            } catch (error: Throwable) {
                state = state.copy(
                    isLoadingMoreTimeline = false
                )
                if (!handleAuthFailure(error)) {
                    state = state.copy(message = error.message ?: stringRes(R.string.msg_load_more_timeline_failed))
                }
            }
        }
    }

    fun search() {
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            state = state.copy(
                searchPosts = emptyList(),
                searchActors = emptyList(),
                isSearchLoading = false,
                hasSearchExecuted = false,
                searchErrorMessage = null
            )
            return
        }
        queueSearch(query = query, immediate = true)
    }

    fun selectProfile(actor: ActorUi) {
        viewModelScope.launch {
            runBusy {
                loadProfile(actor.handle)
                state = state.copy(activeTab = AppTab.Profile)
            }
        }
    }

    fun selectProfileByHandle(handle: String) {
        val normalizedHandle = handle.trim().removePrefix("@")
        if (normalizedHandle.isBlank()) {
            state = state.copy(message = stringRes(R.string.msg_enter_handle))
            return
        }
        viewModelScope.launch {
            runBusy {
                loadProfile(normalizedHandle)
                state = state.copy(activeTab = AppTab.Profile)
            }
        }
    }

    fun submitPost() {
        val text = state.composerText.trim()
        val media = state.composerMedia
        if (text.isBlank() && media == null) {
            state = state.copy(message = stringRes(R.string.msg_enter_post_content))
            return
        }
        if (text.length > MAX_POST_LENGTH) {
            state = state.copy(message = stringRes(R.string.msg_post_max_length, MAX_POST_LENGTH))
            return
        }
        viewModelScope.launch {
            runBusy {
                val draftMedia = media?.let { createDraftMedia(it) }
                repository.createPost(text = text, media = draftMedia)
                state = state.copy(
                    composerText = "",
                    composerMedia = null,
                    composerOpen = false,
                    message = stringRes(R.string.msg_posted)
                )
                refreshTimelineOnly()
                refreshSelfProfileFeed()
            }
        }
    }

    fun deletePost(post: PostUi) {
        viewModelScope.launch {
            runBusy {
                repository.deletePost(post.uri)
                removePostEverywhere(post.uri)
                state = state.copy(message = stringRes(R.string.msg_post_deleted))
            }
        }
    }

    fun toggleLike(post: PostUi) {
        viewModelScope.launch {
            try {
                val newLikeUri = if (post.viewerLikeUri.isNullOrBlank()) {
                    repository.likePost(postUri = post.uri, postCid = post.cid)
                } else {
                    repository.unlikePost(post.viewerLikeUri)
                    null
                }
                updatePostAcrossLists(post.uri) { source ->
                    if (source.viewerLikeUri.isNullOrBlank()) {
                        source.copy(
                            likeCount = source.likeCount + 1,
                            viewerLikeUri = newLikeUri
                        )
                    } else {
                        source.copy(
                            likeCount = (source.likeCount - 1).coerceAtLeast(0),
                            viewerLikeUri = null
                        )
                    }
                }
            } catch (error: Throwable) {
                if (!handleAuthFailure(error)) {
                    state = state.copy(message = error.message ?: stringRes(R.string.msg_like_failed))
                }
            }
        }
    }

    fun toggleRepost(post: PostUi) {
        viewModelScope.launch {
            try {
                val newRepostUri = if (post.viewerRepostUri.isNullOrBlank()) {
                    repository.repostPost(postUri = post.uri, postCid = post.cid)
                } else {
                    repository.unrepost(post.viewerRepostUri)
                    null
                }
                updatePostAcrossLists(post.uri) { source ->
                    if (source.viewerRepostUri.isNullOrBlank()) {
                        source.copy(
                            repostCount = source.repostCount + 1,
                            viewerRepostUri = newRepostUri
                        )
                    } else {
                        source.copy(
                            repostCount = (source.repostCount - 1).coerceAtLeast(0),
                            viewerRepostUri = null
                        )
                    }
                }
            } catch (error: Throwable) {
                if (!handleAuthFailure(error)) {
                    state = state.copy(message = error.message ?: stringRes(R.string.msg_repost_failed))
                }
            }
        }
    }

    fun toggleFollow(actor: ActorUi) {
        viewModelScope.launch {
            runBusy {
                if (actor.viewerFollowingUri.isNullOrBlank()) {
                    repository.follow(actor.did)
                } else {
                    repository.unfollow(requireNotNull(actor.viewerFollowingUri))
                }
                refreshActiveProfile(actor.handle)
            }
        }
    }

    fun runRawXrpc() {
        val endpoint = state.rawEndpoint.trim()
        if (endpoint.isBlank()) {
            state = state.copy(message = stringRes(R.string.msg_enter_xrpc_method))
            return
        }
        viewModelScope.launch {
            runBusy {
                val result = when (state.rawHttpMethod) {
                    RawHttpMethod.GET -> repository.runRawGet(endpoint, state.rawParamsJson)
                    RawHttpMethod.POST -> repository.runRawPost(endpoint, state.rawBodyJson)
                }
                state = state.copy(rawResult = result)
            }
        }
    }

    private suspend fun restoreSession() {
        state = state.copy(isSessionRestoring = true)
        val session = runCatching { repository.restoreSession() }
            .getOrElse { error ->
                state = state.copy(
                    isSessionRestoring = false,
                    message = error.message ?: stringRes(R.string.msg_restore_session_failed)
                )
                null
            }
            ?: run {
                state = state.copy(isSessionRestoring = false)
                return
            }
        state = state.copy(session = session, isSessionRestoring = false)
        runCatching { loadDashboard(session) }
            .onFailure { error ->
                if (!handleAuthFailure(error)) {
                    state = state.copy(message = error.message ?: stringRes(R.string.msg_initial_data_failed))
                }
            }
    }

    private suspend fun handleOAuthRedirect(uri: String) {
        val request = currentPendingOAuthRequest() ?: return
        val redirectUri = Uri.parse(uri)
        val error = redirectUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            clearPendingOAuthRequest()
            val description = redirectUri.getQueryParameter("error_description")
            state = state.copy(
                message = description ?: stringRes(R.string.msg_oauth_cancelled)
            )
            return
        }

        val stateParam = redirectUri.getQueryParameter("state")
        val code = redirectUri.getQueryParameter("code")
        if (stateParam.isNullOrBlank() || stateParam != request.state || code.isNullOrBlank()) {
            clearPendingOAuthRequest()
            state = state.copy(message = stringRes(R.string.msg_oauth_invalid_state))
            return
        }

        clearPendingOAuthRequest()
        runBusy {
            val session = repository.loginWithOAuth(
                code = code,
                codeVerifier = request.codeVerifier,
                redirectUri = request.redirectUri,
                clientId = request.clientId
            )
            state = state.copy(
                session = session,
                oauthLoginUrl = null,
                message = stringRes(R.string.msg_logged_in)
            )
            loadDashboard(session)
        }
    }

    private suspend fun startOAuthFlow(
        createRequest: suspend (redirectUri: String) -> OAuthAuthorizationRequest
    ): OAuthAuthorizationRequest {
        clearPendingOAuthRequest()
        val callbackServer = LoopbackOAuthCallbackServer(
            callbackPath = BlueskyOAuthConfig.LOOPBACK_REDIRECT_PATH
        )
        return runCatching {
            val request = createRequest(callbackServer.redirectUri)
            setPendingOAuthState(
                request = request,
                callbackServer = callbackServer
            )
            request
        }.getOrElse { error ->
            callbackServer.close()
            throw error
        }
    }

    private fun currentPendingOAuthRequest(): OAuthAuthorizationRequest? {
        return synchronized(oauthLock) { sharedPendingOAuthRequest }
    }

    private fun setPendingOAuthState(
        request: OAuthAuthorizationRequest,
        callbackServer: LoopbackOAuthCallbackServer
    ) {
        synchronized(oauthLock) {
            sharedPendingOAuthRequest = request
            sharedCallbackServer = callbackServer
            sharedAwaitJob?.cancel()
            sharedAwaitJob = oauthScope.launch {
                try {
                    val callbackUri = callbackServer.awaitCallbackUri()
                    oauthResultChannel.trySend(Result.success(callbackUri))
                } catch (_: CancellationException) {
                    // Explicit cancellation means a new auth attempt replaced this one.
                } catch (error: Throwable) {
                    oauthResultChannel.trySend(Result.failure(error))
                } finally {
                    synchronized(oauthLock) {
                        if (sharedCallbackServer === callbackServer) {
                            sharedCallbackServer?.close()
                            sharedCallbackServer = null
                        }
                        if (sharedAwaitJob === this) {
                            sharedAwaitJob = null
                        }
                    }
                }
            }
        }
    }

    private fun clearPendingOAuthRequest() {
        synchronized(oauthLock) {
            sharedPendingOAuthRequest = null
            sharedAwaitJob?.cancel()
            sharedAwaitJob = null
            sharedCallbackServer?.close()
            sharedCallbackServer = null
        }
    }

    private suspend fun loadDashboard(session: AuthSession) {
        val timeline = repository.getTimeline(
            limit = INITIAL_TIMELINE_LOAD_LIMIT,
            forceRefresh = true
        )
        state = state.copy(
            timeline = timeline.posts,
            timelineCursor = timeline.cursor
        )
        prefetchHomeTimeline(cursor = timeline.cursor)
        preloadDashboardDetails(session)
    }

    private fun prefetchHomeTimeline(cursor: String?) {
        if (cursor.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.getTimeline(
                    limit = HOME_TIMELINE_PREFETCH_LIMIT,
                    cursor = cursor
                )
            }.onSuccess { nextPage ->
                val merged = (state.timeline + nextPage.posts).distinctBy { it.uri }
                state = state.copy(
                    timeline = merged,
                    timelineCursor = nextPage.cursor ?: state.timelineCursor
                )
            }
        }
    }

    private fun preloadDashboardDetails(session: AuthSession) {
        viewModelScope.launch {
            runCatching { repository.getNotifications() }
                .onSuccess { notifications ->
                    state = state.copy(notifications = notifications.notifications)
                }
        }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val profileDeferred = async { repository.getProfile(session.handle) }
                    val profileFeedDeferred = async { repository.getAuthorFeed(session.handle) }
                    val followsDeferred = async { repository.getFollows(session.handle) }
                    val followersDeferred = async { repository.getFollowers(session.handle) }
                    val profile = profileDeferred.await()
                    val profileFeed = profileFeedDeferred.await().posts
                    val follows = followsDeferred.await().actors
                    val followers = followersDeferred.await().actors
                    Quadruple(profile, profileFeed, follows, followers)
                }
            }.onSuccess { (profile, profileFeed, follows, followers) ->
                val activeIsSelf = state.activeProfile == null ||
                    state.activeProfile?.did == state.session?.did ||
                    state.activeProfile?.handle.equals(session.handle, ignoreCase = true)
                state = state.copy(
                    selfProfile = profile,
                    selfProfileFeed = profileFeed,
                    selfFollows = follows,
                    selfFollowers = followers,
                    activeProfile = if (activeIsSelf) profile else state.activeProfile,
                    activeProfileFeed = if (activeIsSelf) profileFeed else state.activeProfileFeed,
                    follows = if (activeIsSelf) follows else state.follows,
                    followers = if (activeIsSelf) followers else state.followers
                )
            }
        }
    }

    private suspend fun refreshTimelineOnly() {
        val timeline = repository.getTimeline(
            limit = INITIAL_TIMELINE_LOAD_LIMIT,
            forceRefresh = true
        )
        state = state.copy(timeline = timeline.posts, timelineCursor = timeline.cursor)
        prefetchHomeTimeline(cursor = timeline.cursor)
    }

    private suspend fun refreshTimelineWithNewest() {
        val currentTimeline = state.timeline
        val previousTopUri = currentTimeline.firstOrNull()?.uri?.takeIf { it.isNotBlank() }
        val firstPage = repository.getTimeline(
            limit = INITIAL_TIMELINE_LOAD_LIMIT,
            forceRefresh = true
        )

        if (previousTopUri == null) {
            state = state.copy(timeline = firstPage.posts, timelineCursor = firstPage.cursor)
            return
        }

        val collected = mutableListOf<PostUi>()
        val seenUris = linkedSetOf<String>()
        var foundPreviousTop = false
        var cursor = firstPage.cursor
        var pagePosts = firstPage.posts
        var scannedPages = 0

        while (true) {
            for (post in pagePosts) {
                val uri = post.uri
                if (uri.isBlank()) continue
                if (uri == previousTopUri) {
                    foundPreviousTop = true
                    break
                }
                if (seenUris.add(uri)) {
                    collected += post
                }
            }
            if (foundPreviousTop || cursor.isNullOrBlank() || scannedPages >= MAX_REFRESH_SCAN_PAGES) {
                break
            }
            val nextPage = repository.getTimeline(
                limit = HOME_TIMELINE_PAGE_LIMIT,
                cursor = cursor,
                forceRefresh = true
            )
            pagePosts = nextPage.posts
            cursor = nextPage.cursor
            scannedPages++
        }

        if (collected.isEmpty()) {
            state = state.copy(timeline = firstPage.posts, timelineCursor = firstPage.cursor)
            return
        }

        val merged = (collected + currentTimeline).distinctBy { it.uri }
        state = state.copy(
            timeline = merged,
            timelineCursor = firstPage.cursor
        )
    }

    private suspend fun refreshActiveProfileFeed() {
        val actor = state.activeProfile?.handle ?: return
        val feed = repository.getAuthorFeed(actor, forceRefresh = true)
        state = state.copy(activeProfileFeed = feed.posts)
    }

    private suspend fun refreshSelfProfileFeed() {
        val actor = state.selfProfile?.handle ?: state.session?.handle ?: return
        val feed = repository.getAuthorFeed(actor, forceRefresh = true).posts
        val activeIsSelf = state.activeProfile?.did == state.selfProfile?.did &&
            !state.selfProfile?.did.isNullOrBlank()
        state = state.copy(
            selfProfileFeed = feed,
            activeProfileFeed = if (activeIsSelf) feed else state.activeProfileFeed
        )
    }

    private suspend fun refreshActiveProfile(actor: String) {
        loadProfile(actor = actor, forceRefresh = true)
    }

    private suspend fun loadProfile(actor: String, forceRefresh: Boolean = false) {
        coroutineScope {
            val profile = async { repository.getProfile(actor, forceRefresh = forceRefresh) }
            val feed = async { repository.getAuthorFeed(actor, forceRefresh = forceRefresh) }
            val follows = async { repository.getFollows(actor, forceRefresh = forceRefresh) }
            val followers = async { repository.getFollowers(actor, forceRefresh = forceRefresh) }
            val resolvedProfile = profile.await()
            val resolvedFeed = feed.await().posts
            val resolvedFollows = follows.await().actors
            val resolvedFollowers = followers.await().actors
            val isSelfProfile = resolvedProfile.did == state.session?.did ||
                resolvedProfile.handle.equals(state.session?.handle, ignoreCase = true)
            state = state.copy(
                selfProfile = if (isSelfProfile) resolvedProfile else state.selfProfile,
                selfProfileFeed = if (isSelfProfile) resolvedFeed else state.selfProfileFeed,
                selfFollows = if (isSelfProfile) resolvedFollows else state.selfFollows,
                selfFollowers = if (isSelfProfile) resolvedFollowers else state.selfFollowers,
                activeProfile = resolvedProfile,
                activeProfileFeed = resolvedFeed,
                follows = resolvedFollows,
                followers = resolvedFollowers
            )
        }
    }

    private fun updatePostAcrossLists(postUri: String, transform: (PostUi) -> PostUi) {
        val updateList: (List<PostUi>) -> List<PostUi> = { list ->
            list.map { item -> if (item.uri == postUri) transform(item) else item }
        }
        state = state.copy(
            timeline = updateList(state.timeline),
            searchPosts = updateList(state.searchPosts),
            selfProfileFeed = updateList(state.selfProfileFeed),
            activeProfileFeed = updateList(state.activeProfileFeed)
        )
    }

    private fun removePostEverywhere(postUri: String) {
        val filterList: (List<PostUi>) -> List<PostUi> = { list ->
            list.filterNot { it.uri == postUri }
        }
        state = state.copy(
            timeline = filterList(state.timeline),
            searchPosts = filterList(state.searchPosts),
            selfProfileFeed = filterList(state.selfProfileFeed),
            activeProfileFeed = filterList(state.activeProfileFeed)
        )
    }

    private fun updatePostTranslation(postUri: String, transform: (PostTranslationState) -> PostTranslationState) {
        val current = state.postTranslations[postUri] ?: PostTranslationState()
        state = state.copy(
            postTranslations = state.postTranslations + (postUri to transform(current))
        )
    }

    private fun canTranslateBetween(sourceLanguageTag: String, targetLanguageTag: String): Boolean {
        val sourceLanguage = translationService.resolveMlKitLanguage(sourceLanguageTag) ?: return false
        val targetLanguage = translationService.resolveMlKitLanguage(targetLanguageTag) ?: return false
        return sourceLanguage != targetLanguage
    }

    private fun getCurrentAppLanguageTag(): String {
        val localeList: LocaleList = getApplication<Application>().resources.configuration.locales
        val locale = localeList.get(0) ?: Locale.getDefault()
        return locale.toLanguageTag()
    }

    private suspend fun runBusy(block: suspend () -> Unit) {
        state = state.copy(isBusy = true)
        runCatching { block() }
            .onFailure { error ->
                if (!handleAuthFailure(error)) {
                    val message = error.message ?: stringRes(R.string.msg_unknown_error)
                    state = state.copy(message = message)
                }
            }
        state = state.copy(isBusy = false)
    }

    private suspend fun handleAuthFailure(error: Throwable): Boolean {
        val message = error.message ?: return false
        if (!isAuthFailureMessage(message)) return false
        val refreshed = runCatching { repository.refreshSession() }.getOrNull()
        state = if (refreshed != null) {
            state.copy(
                session = refreshed,
                message = stringRes(R.string.msg_session_refreshed_retry)
            )
        } else {
            repository.logout()
            state.copy(
                session = null,
                message = stringRes(R.string.msg_auth_error_relogin)
            )
        }
        return true
    }

    private fun isAuthFailureMessage(message: String): Boolean {
        val isHttp400TokenFailure = message.contains("HTTP 400", ignoreCase = true) && (
            message.contains("ExpiredToken", ignoreCase = true) ||
                message.contains("InvalidToken", ignoreCase = true) ||
                message.contains("token has expired", ignoreCase = true)
            )
        return message.contains("Session expired", ignoreCase = true) ||
            message.contains("Not authenticated", ignoreCase = true) ||
            message.contains("HTTP 401", ignoreCase = true) ||
            isHttp400TokenFailure
    }

    companion object {
        private const val MAX_POST_LENGTH = 300
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val SEARCH_USERS_LIMIT = 60
        private const val INITIAL_TIMELINE_LOAD_LIMIT = 20
        private const val HOME_TIMELINE_PREFETCH_LIMIT = 20
        private const val HOME_TIMELINE_PAGE_LIMIT = 40
        private const val MAX_REFRESH_SCAN_PAGES = 5
        private val oauthLock = Any()
        private val oauthScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val oauthResultChannel = Channel<Result<String>>(capacity = Channel.BUFFERED)
        @Volatile
        private var sharedPendingOAuthRequest: OAuthAuthorizationRequest? = null
        @Volatile
        private var sharedCallbackServer: LoopbackOAuthCallbackServer? = null
        @Volatile
        private var sharedAwaitJob: Job? = null
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private fun queueSearch(query: String, immediate: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        state = state.copy(
            isSearchLoading = true,
            hasSearchExecuted = true,
            searchErrorMessage = null
        )
        runCatching {
            coroutineScope {
                val posts = async { repository.searchPosts(query) }
                val actors = async { repository.searchActors(query, limit = SEARCH_USERS_LIMIT) }
                state = state.copy(
                    searchPosts = posts.await(),
                    searchActors = actors.await(),
                    isSearchLoading = false,
                    searchErrorMessage = null
                )
            }
        }.onFailure { error ->
            state = state.copy(isSearchLoading = false)
            if (!handleAuthFailure(error)) {
                state = state.copy(searchErrorMessage = error.message ?: stringRes(R.string.msg_search_failed))
            }
        }
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String {
        val fallback = uri.lastPathSegment ?: "attachment"
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex == -1 || !cursor.moveToFirst()) return@use null
                cursor.getString(nameIndex)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun createDraftMedia(media: ComposerMediaUi): DraftMediaAttachment {
        val uri = Uri.parse(media.uri)
        val bytes = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        }.getOrNull() ?: throw IllegalStateException(stringRes(R.string.msg_media_read_failed))
        if (bytes.isEmpty()) {
            throw IllegalStateException(stringRes(R.string.msg_media_read_failed))
        }
        val type = when (media.type) {
            ComposerMediaType.Image -> DraftMediaType.Image
            ComposerMediaType.Video -> DraftMediaType.Video
        }
        return DraftMediaAttachment(
            type = type,
            mimeType = media.mimeType,
            bytes = bytes
        )
    }

    private fun stringRes(@StringRes id: Int, vararg args: Any): String {
        return getApplication<Application>().getString(id, *args)
    }
}
