package com.example.bluesky_clientapp_kotlin.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluesky_clientapp_kotlin.R
import com.example.bluesky_clientapp_kotlin.data.model.ActorUi
import com.example.bluesky_clientapp_kotlin.data.model.AuthSession
import com.example.bluesky_clientapp_kotlin.data.model.DraftMediaAttachment
import com.example.bluesky_clientapp_kotlin.data.model.DraftMediaType
import com.example.bluesky_clientapp_kotlin.data.model.NotificationUi
import com.example.bluesky_clientapp_kotlin.data.model.PostUi
import com.example.bluesky_clientapp_kotlin.data.network.ATProtoKitClient
import com.example.bluesky_clientapp_kotlin.data.repository.BlueskyRepository
import com.example.bluesky_clientapp_kotlin.data.store.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

data class UiState(
    val isBusy: Boolean = false,
    val identifier: String = "",
    val appPassword: String = "",
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

    var state by mutableStateOf(UiState())
        private set
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            restoreSession()
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    fun updateIdentifier(value: String) {
        state = state.copy(identifier = value)
    }

    fun updateAppPassword(value: String) {
        state = state.copy(appPassword = value)
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
                isPostThreadLoading = true
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
            isPostThreadLoading = false
        )
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
        if (state.identifier.isBlank() || state.appPassword.isBlank()) {
            state = state.copy(message = stringRes(R.string.msg_enter_id_password))
            return
        }
        viewModelScope.launch {
            runBusy {
                val session = repository.login(
                    identifier = state.identifier.trim(),
                    appPassword = state.appPassword.trim()
                )
                state = state.copy(
                    session = session,
                    appPassword = "",
                    message = stringRes(R.string.msg_logged_in)
                )
                loadDashboard(session)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runBusy {
                repository.logout()
                state = UiState(message = stringRes(R.string.msg_logged_out))
            }
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            val session = state.session ?: return@launch
            runBusy {
                loadDashboard(session)
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
        val session = runCatching { repository.restoreSession() }
            .getOrElse { error ->
                state = state.copy(message = error.message ?: stringRes(R.string.msg_restore_session_failed))
                null
            }
            ?: return
        state = state.copy(session = session)
        runCatching { loadDashboard(session) }
            .onFailure { error ->
                if (!handleAuthFailure(error)) {
                    state = state.copy(message = error.message ?: stringRes(R.string.msg_initial_data_failed))
                }
            }
    }

    private suspend fun loadDashboard(session: AuthSession) {
        coroutineScope {
            val timelineDeferred = async { repository.getTimeline() }
            val notificationsDeferred = async { repository.getNotifications() }
            val profileDeferred = async { repository.getProfile(session.handle) }
            val profileFeedDeferred = async { repository.getAuthorFeed(session.handle) }
            val followsDeferred = async { repository.getFollows(session.handle) }
            val followersDeferred = async { repository.getFollowers(session.handle) }
            val timeline = timelineDeferred.await()
            val notifications = notificationsDeferred.await()
            val profile = profileDeferred.await()
            val profileFeed = profileFeedDeferred.await()
            val follows = followsDeferred.await()
            val followers = followersDeferred.await()
            state = state.copy(
                timeline = timeline.posts,
                timelineCursor = timeline.cursor,
                notifications = notifications.notifications,
                selfProfile = profile,
                selfProfileFeed = profileFeed.posts,
                selfFollows = follows.actors,
                selfFollowers = followers.actors,
                activeProfile = profile,
                activeProfileFeed = profileFeed.posts,
                follows = follows.actors,
                followers = followers.actors
            )
        }
    }

    private suspend fun refreshTimelineOnly() {
        val timeline = repository.getTimeline()
        state = state.copy(timeline = timeline.posts, timelineCursor = timeline.cursor)
    }

    private suspend fun refreshActiveProfileFeed() {
        val actor = state.activeProfile?.handle ?: return
        val feed = repository.getAuthorFeed(actor)
        state = state.copy(activeProfileFeed = feed.posts)
    }

    private suspend fun refreshSelfProfileFeed() {
        val actor = state.selfProfile?.handle ?: state.session?.handle ?: return
        val feed = repository.getAuthorFeed(actor).posts
        val activeIsSelf = state.activeProfile?.did == state.selfProfile?.did &&
            !state.selfProfile?.did.isNullOrBlank()
        state = state.copy(
            selfProfileFeed = feed,
            activeProfileFeed = if (activeIsSelf) feed else state.activeProfileFeed
        )
    }

    private suspend fun refreshActiveProfile(actor: String) {
        loadProfile(actor)
    }

    private suspend fun loadProfile(actor: String) {
        coroutineScope {
            val profile = async { repository.getProfile(actor) }
            val feed = async { repository.getAuthorFeed(actor) }
            val follows = async { repository.getFollows(actor) }
            val followers = async { repository.getFollowers(actor) }
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
            state.copy(
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
    }

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
