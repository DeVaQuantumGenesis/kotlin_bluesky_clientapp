package com.example.bluesky_clientapp_kotlin.data.repository

import com.example.bluesky_clientapp_kotlin.data.model.ActorPage
import com.example.bluesky_clientapp_kotlin.data.model.ActorUi
import com.example.bluesky_clientapp_kotlin.data.model.AuthSession
import com.example.bluesky_clientapp_kotlin.data.model.DraftMediaAttachment
import com.example.bluesky_clientapp_kotlin.data.model.DraftMediaType
import com.example.bluesky_clientapp_kotlin.data.model.NotificationPage
import com.example.bluesky_clientapp_kotlin.data.model.PostUi
import com.example.bluesky_clientapp_kotlin.data.model.PostThreadUi
import com.example.bluesky_clientapp_kotlin.data.model.TimelinePage
import com.example.bluesky_clientapp_kotlin.data.network.ATProtoKitClient
import com.example.bluesky_clientapp_kotlin.data.network.OAuthAuthorizationRequest
import com.example.bluesky_clientapp_kotlin.data.network.arrayValue
import com.example.bluesky_clientapp_kotlin.data.network.asObjectOrNull
import com.example.bluesky_clientapp_kotlin.data.network.objectValue
import com.example.bluesky_clientapp_kotlin.data.network.prettyContent
import com.example.bluesky_clientapp_kotlin.data.network.stringValue
import com.example.bluesky_clientapp_kotlin.data.network.toActorUi
import com.example.bluesky_clientapp_kotlin.data.network.toNotificationUi
import com.example.bluesky_clientapp_kotlin.data.network.toPostUi
import com.example.bluesky_clientapp_kotlin.data.store.SessionStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class BlueskyRepository(
    private val client: ATProtoKitClient,
    private val sessionStore: SessionStore,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }
) {
    private val timelineCache = ConcurrentHashMap<String, CacheEntry<TimelinePage>>()
    private val authorFeedCache = ConcurrentHashMap<String, CacheEntry<TimelinePage>>()
    private val profileCache = ConcurrentHashMap<String, CacheEntry<ActorUi>>()
    private val followersCache = ConcurrentHashMap<String, CacheEntry<ActorPage>>()
    private val followsCache = ConcurrentHashMap<String, CacheEntry<ActorPage>>()

    suspend fun restoreSession(): AuthSession? {
        val session = sessionStore.sessionFlow.firstOrNull()
        client.setSession(session)
        return session
    }

    suspend fun createOAuthAuthorizationRequest(redirectUri: String): OAuthAuthorizationRequest {
        return client.createOAuthAuthorizationRequest(redirectUri = redirectUri)
    }

    suspend fun loginWithOAuth(
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String
    ): AuthSession {
        val session = client.createOAuthSession(
            code = code,
            codeVerifier = codeVerifier,
            redirectUri = redirectUri,
            clientId = clientId
        )
        sessionStore.saveSession(session)
        clearCaches()
        return session
    }

    suspend fun logout() {
        client.setSession(null)
        sessionStore.clearSession()
        clearCaches()
    }

    fun close() {
        client.close()
    }

    suspend fun refreshSession(): AuthSession {
        val refreshed = client.refreshSession()
        sessionStore.saveSession(refreshed)
        return refreshed
    }

    suspend fun getTimeline(limit: Int = 40, cursor: String? = null, forceRefresh: Boolean = false): TimelinePage {
        val cacheKey = cacheTimelineKey(limit = limit, cursor = cursor)
        if (!forceRefresh) {
            timelineCache.freshValue(cacheKey, TIMELINE_CACHE_TTL_MS)?.let { return it }
        }

        val discoverQuery = linkedMapOf(
            "limit" to limit.toString(),
            "sort" to "latest",
            "q" to "*"
        )
        if (!cursor.isNullOrBlank()) discoverQuery["cursor"] = cursor

        val timelineResponse = runCatching {
            // Prefer global latest posts for the home/discover feed.
            getAndSyncSession("app.bsky.feed.searchPosts", discoverQuery)
        }.getOrElse { discoverError ->
            if (isInvalidSessionError(discoverError.message)) throw discoverError
            val fallbackQuery = linkedMapOf("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) fallbackQuery["cursor"] = cursor
            getAndSyncSession("app.bsky.feed.getTimeline", fallbackQuery)
        }

        val timelinePosts = timelineResponse.arrayValue("posts").mapNotNull { it.asObjectOrNull()?.toPostUi() }
            .ifEmpty {
                timelineResponse.arrayValue("feed").mapNotNull { it.asObjectOrNull()?.toPostUi() }
            }
            .sortedByDescending { it.indexedAt }

        val result = TimelinePage(
            posts = timelinePosts,
            cursor = timelineResponse.stringValue("cursor")
        )
        timelineCache[cacheKey] = CacheEntry(value = result)
        return result
    }

    suspend fun getAuthorFeed(actor: String, limit: Int = 40, forceRefresh: Boolean = false): TimelinePage {
        val normalizedActor = normalizeActor(actor)
        val cacheKey = "$normalizedActor|$limit"
        if (!forceRefresh) {
            authorFeedCache.freshValue(cacheKey, AUTHOR_FEED_CACHE_TTL_MS)?.let { return it }
        }
        val response = getAndSyncSession(
            endpoint = "app.bsky.feed.getAuthorFeed",
            query = mapOf("actor" to actor, "limit" to limit.toString())
        )
        val result = TimelinePage(
            posts = response.arrayValue("feed").mapNotNull { it.asObjectOrNull()?.toPostUi() },
            cursor = response.stringValue("cursor")
        )
        authorFeedCache[cacheKey] = CacheEntry(value = result)
        return result
    }

    suspend fun getProfile(actor: String, forceRefresh: Boolean = false): ActorUi {
        val cacheKey = normalizeActor(actor)
        if (!forceRefresh) {
            profileCache.freshValue(cacheKey, PROFILE_CACHE_TTL_MS)?.let { return it }
        }
        val response = getAndSyncSession(
            endpoint = "app.bsky.actor.getProfile",
            query = mapOf("actor" to actor)
        )
        return response.toActorUi().also { profileCache[cacheKey] = CacheEntry(value = it) }
    }

    suspend fun getPostThread(postUri: String, depth: Int = 6): PostThreadUi {
        val response = getAndSyncSession(
            endpoint = "app.bsky.feed.getPostThread",
            query = mapOf(
                "uri" to postUri,
                "depth" to depth.toString()
            )
        )
        val thread = response.objectValue("thread")
            ?: throw IllegalStateException("Post thread is empty")
        val root = thread.objectValue("post")?.toPostUi() ?: thread.toPostUi()
        val replies = mutableListOf<PostUi>()
        collectThreadReplies(thread, replies)
        val dedupReplies = replies
            .filterNot { it.uri == root.uri }
            .distinctBy { it.uri }
        return PostThreadUi(root = root, replies = dedupReplies)
    }

    suspend fun getNotifications(limit: Int = 50): NotificationPage {
        val response = getAndSyncSession(
            endpoint = "app.bsky.notification.listNotifications",
            query = mapOf("limit" to limit.toString())
        )
        return NotificationPage(
            notifications = response.arrayValue("notifications").mapNotNull {
                it.asObjectOrNull()?.toNotificationUi()
            },
            cursor = response.stringValue("cursor")
        )
    }

    suspend fun searchPosts(query: String, limit: Int = 30): List<PostUi> {
        if (query.isBlank()) return emptyList()
        val response = getAndSyncSession(
            endpoint = "app.bsky.feed.searchPosts",
            query = mapOf("q" to query, "limit" to limit.toString())
        )
        return response.arrayValue("posts").mapNotNull { it.asObjectOrNull()?.toPostUi() }
    }

    suspend fun searchActors(query: String, limit: Int = 30): List<ActorUi> {
        if (query.isBlank()) return emptyList()
        val response = getAndSyncSession(
            endpoint = "app.bsky.actor.searchActors",
            query = mapOf("q" to query, "limit" to limit.toString())
        )
        return response.arrayValue("actors").mapNotNull { it.asObjectOrNull()?.toActorUi() }
    }

    suspend fun getFollowers(actor: String, limit: Int = 60, forceRefresh: Boolean = false): ActorPage {
        val normalizedActor = normalizeActor(actor)
        val cacheKey = "$normalizedActor|$limit"
        if (!forceRefresh) {
            followersCache.freshValue(cacheKey, RELATION_CACHE_TTL_MS)?.let { return it }
        }
        val response = getAndSyncSession(
            endpoint = "app.bsky.graph.getFollowers",
            query = mapOf("actor" to actor, "limit" to limit.toString())
        )
        val result = ActorPage(
            actors = response.arrayValue("followers").mapNotNull { it.asObjectOrNull()?.toActorUi() },
            cursor = response.stringValue("cursor")
        )
        followersCache[cacheKey] = CacheEntry(value = result)
        return result
    }

    suspend fun getFollows(actor: String, limit: Int = 60, forceRefresh: Boolean = false): ActorPage {
        val normalizedActor = normalizeActor(actor)
        val cacheKey = "$normalizedActor|$limit"
        if (!forceRefresh) {
            followsCache.freshValue(cacheKey, RELATION_CACHE_TTL_MS)?.let { return it }
        }
        val response = getAndSyncSession(
            endpoint = "app.bsky.graph.getFollows",
            query = mapOf("actor" to actor, "limit" to limit.toString())
        )
        val result = ActorPage(
            actors = response.arrayValue("follows").mapNotNull { it.asObjectOrNull()?.toActorUi() },
            cursor = response.stringValue("cursor")
        )
        followsCache[cacheKey] = CacheEntry(value = result)
        return result
    }

    suspend fun createPost(text: String, media: DraftMediaAttachment? = null): String {
        val embed = media?.let { createEmbedRecord(it) }
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.post")
            put("text", text)
            put("createdAt", Instant.now().toString())
            if (embed != null) {
                put("embed", embed)
            }
        }
        val uri = createRecord(collection = "app.bsky.feed.post", record = record)
        invalidateSocialCaches()
        return uri
    }

    suspend fun deletePost(postUri: String) {
        deleteRecord(recordUri = postUri, fallbackCollection = "app.bsky.feed.post")
        invalidateSocialCaches()
    }

    suspend fun likePost(postUri: String, postCid: String): String {
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.like")
            put("createdAt", Instant.now().toString())
            putJsonObject("subject") {
                put("uri", postUri)
                put("cid", postCid)
            }
        }
        val uri = createRecord(collection = "app.bsky.feed.like", record = record)
        invalidateSocialCaches()
        return uri
    }

    suspend fun unlikePost(likeUri: String) {
        deleteRecord(recordUri = likeUri, fallbackCollection = "app.bsky.feed.like")
        invalidateSocialCaches()
    }

    suspend fun repostPost(postUri: String, postCid: String): String {
        val record = buildJsonObject {
            put("\$type", "app.bsky.feed.repost")
            put("createdAt", Instant.now().toString())
            putJsonObject("subject") {
                put("uri", postUri)
                put("cid", postCid)
            }
        }
        val uri = createRecord(collection = "app.bsky.feed.repost", record = record)
        invalidateSocialCaches()
        return uri
    }

    suspend fun unrepost(repostUri: String) {
        deleteRecord(recordUri = repostUri, fallbackCollection = "app.bsky.feed.repost")
        invalidateSocialCaches()
    }

    suspend fun follow(actorDid: String): String {
        val record = buildJsonObject {
            put("\$type", "app.bsky.graph.follow")
            put("subject", actorDid)
            put("createdAt", Instant.now().toString())
        }
        val uri = createRecord(collection = "app.bsky.graph.follow", record = record)
        invalidateSocialCaches()
        return uri
    }

    suspend fun unfollow(followUri: String) {
        deleteRecord(recordUri = followUri, fallbackCollection = "app.bsky.graph.follow")
        invalidateSocialCaches()
    }

    suspend fun runRawGet(method: String, paramsJson: String): String {
        validateMethod(method)
        val params = parseQueryParams(paramsJson)
        val response = getAndSyncSession(endpoint = method.trim(), query = params)
        return json.encodeToString(JsonObject.serializer(), response)
    }

    suspend fun runRawPost(method: String, bodyJson: String): String {
        validateMethod(method)
        val body = runCatching {
            json.parseToJsonElement(bodyJson).asObjectOrNull() ?: JsonObject(emptyMap())
        }.getOrDefault(JsonObject(emptyMap()))
        val response = postAndSyncSession(endpoint = method.trim(), body = body)
        return json.encodeToString(JsonObject.serializer(), response)
    }

    private suspend fun createRecord(collection: String, record: JsonObject): String {
        val did = requireNotNull(client.currentSession()?.did) { "Not authenticated" }
        val response = postAndSyncSession(
            endpoint = "com.atproto.repo.createRecord",
            body = buildJsonObject {
                put("repo", did)
                put("collection", collection)
                put("record", record)
            }
        )
        return response.stringValue("uri")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("createRecord returned empty uri")
    }

    private suspend fun createEmbedRecord(media: DraftMediaAttachment): JsonObject {
        val uploadResponse = uploadBlobAndSyncSession(media.bytes, media.mimeType)
        val blob = uploadResponse.objectValue("blob") ?: uploadResponse
        return when (media.type) {
            DraftMediaType.Image -> buildJsonObject {
                put("\$type", "app.bsky.embed.images")
                putJsonArray("images") {
                    add(
                        buildJsonObject {
                            put("alt", media.alt)
                            put("image", blob)
                        }
                    )
                }
            }

            DraftMediaType.Video -> buildJsonObject {
                put("\$type", "app.bsky.embed.video")
                put("video", blob)
                if (media.alt.isNotBlank()) {
                    put("alt", media.alt)
                }
            }
        }
    }

    private suspend fun deleteRecord(recordUri: String, fallbackCollection: String) {
        val did = requireNotNull(client.currentSession()?.did) { "Not authenticated" }
        val parsed = parseAtUri(recordUri)
        postAndSyncSession(
            endpoint = "com.atproto.repo.deleteRecord",
            body = buildJsonObject {
                put("repo", did)
                put("collection", parsed?.first ?: fallbackCollection)
                val rkey = parsed?.second ?: recordUri.substringAfterLast('/').takeIf { it.isNotBlank() }
                require(!rkey.isNullOrBlank()) { "Invalid record uri: $recordUri" }
                put("rkey", rkey)
            }
        )
    }

    private fun parseQueryParams(source: String): Map<String, String> {
        if (source.isBlank()) return emptyMap()
        val root = runCatching {
            json.parseToJsonElement(source).asObjectOrNull() ?: JsonObject(emptyMap())
        }.getOrDefault(JsonObject(emptyMap()))
        return root.mapValues { (_, value) ->
            when {
                value is JsonPrimitive && value.isString -> value.content
                else -> value.prettyContent()
            }
        }
    }

    private fun parseAtUri(uri: String): Pair<String, String>? {
        if (!uri.startsWith("at://")) return null
        val parts = uri.removePrefix("at://").split("/")
        if (parts.size < 3) return null
        return parts[1] to parts[2]
    }

    private fun validateMethod(method: String) {
        val trimmed = method.trim()
        require(trimmed.isNotBlank()) { "XRPC method is empty" }
        require(trimmed.matches(Regex("^[a-zA-Z0-9.]+$"))) { "Invalid XRPC method: $trimmed" }
    }

    private fun collectThreadReplies(node: JsonObject, sink: MutableList<PostUi>) {
        node.arrayValue("replies").forEach { replyNodeElement ->
            val replyNode = replyNodeElement.asObjectOrNull() ?: return@forEach
            val replyPost = replyNode.objectValue("post")?.toPostUi() ?: replyNode.toPostUi()
            if (replyPost.uri.isNotBlank()) {
                sink += replyPost
            }
            collectThreadReplies(replyNode, sink)
        }
    }

    private fun isInvalidSessionError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("HTTP 401", ignoreCase = true) ||
            message.contains("Session expired", ignoreCase = true) ||
            message.contains("InvalidToken", ignoreCase = true) ||
            message.contains("ExpiredToken", ignoreCase = true)
    }

    private suspend fun getAndSyncSession(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        requiresAuth: Boolean = true
    ): JsonObject {
        val response = client.get(endpoint = endpoint, query = query, requiresAuth = requiresAuth)
        syncCurrentSession()
        return response
    }

    private suspend fun postAndSyncSession(
        endpoint: String,
        body: JsonObject = JsonObject(emptyMap()),
        requiresAuth: Boolean = true
    ): JsonObject {
        val response = client.post(endpoint = endpoint, body = body, requiresAuth = requiresAuth)
        syncCurrentSession()
        return response
    }

    private suspend fun uploadBlobAndSyncSession(
        bytes: ByteArray,
        mimeType: String
    ): JsonObject {
        val response = client.uploadBlob(bytes = bytes, mimeType = mimeType)
        syncCurrentSession()
        return response
    }

    private suspend fun syncCurrentSession() {
        client.currentSession()?.let { sessionStore.saveSession(it) }
    }

    private fun invalidateSocialCaches() {
        timelineCache.clear()
        authorFeedCache.clear()
        profileCache.clear()
        followersCache.clear()
        followsCache.clear()
    }

    private fun clearCaches() {
        invalidateSocialCaches()
    }

    private fun normalizeActor(actor: String): String {
        return actor.trim().removePrefix("@").lowercase()
    }

    private fun cacheTimelineKey(limit: Int, cursor: String?): String {
        return "$limit|${cursor.orEmpty()}"
    }

    private data class CacheEntry<T>(
        val value: T,
        val createdAtMs: Long = System.currentTimeMillis()
    )

    private fun <T> ConcurrentHashMap<String, CacheEntry<T>>.freshValue(
        key: String,
        ttlMs: Long
    ): T? {
        val entry = this[key] ?: return null
        return if (System.currentTimeMillis() - entry.createdAtMs <= ttlMs) {
            entry.value
        } else {
            remove(key)
            null
        }
    }

    companion object {
        private const val TIMELINE_CACHE_TTL_MS = 20_000L
        private const val AUTHOR_FEED_CACHE_TTL_MS = 45_000L
        private const val PROFILE_CACHE_TTL_MS = 120_000L
        private const val RELATION_CACHE_TTL_MS = 60_000L
    }
}
