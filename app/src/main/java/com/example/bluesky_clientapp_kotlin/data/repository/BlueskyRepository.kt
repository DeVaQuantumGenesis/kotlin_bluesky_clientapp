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

class BlueskyRepository(
    private val client: ATProtoKitClient,
    private val sessionStore: SessionStore,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }
) {
    suspend fun restoreSession(): AuthSession? {
        val session = sessionStore.sessionFlow.firstOrNull()
        client.setSession(session)
        if (session == null) return null
        return runCatching {
            val refreshed = client.refreshSession()
            sessionStore.saveSession(refreshed)
            refreshed
        }.getOrElse {
            // Keep persisted session unless user explicitly logs out.
            session
        }
    }

    suspend fun login(identifier: String, appPassword: String): AuthSession {
        val session = client.createSession(identifier = identifier, appPassword = appPassword)
        sessionStore.saveSession(session)
        return session
    }

    suspend fun logout() {
        client.setSession(null)
        sessionStore.clearSession()
    }

    fun close() {
        client.close()
    }

    suspend fun refreshSession(): AuthSession {
        val refreshed = client.refreshSession()
        sessionStore.saveSession(refreshed)
        return refreshed
    }

    suspend fun getTimeline(limit: Int = 40, cursor: String? = null): TimelinePage {
        val timelineQuery = linkedMapOf("limit" to limit.toString())
        if (!cursor.isNullOrBlank()) timelineQuery["cursor"] = cursor

        val timelineResponse = runCatching {
            getAndSyncSession("app.bsky.feed.getTimeline", timelineQuery)
        }.getOrElse { timelineError ->
            if (isInvalidSessionError(timelineError.message)) throw timelineError
            val fallbackQuery = linkedMapOf(
                "limit" to limit.toString(),
                "sort" to "latest",
                "q" to "*"
            )
            if (!cursor.isNullOrBlank()) fallbackQuery["cursor"] = cursor
            getAndSyncSession("app.bsky.feed.searchPosts", fallbackQuery)
        }

        val timelinePosts = timelineResponse.arrayValue("feed").mapNotNull { it.asObjectOrNull()?.toPostUi() }
            .ifEmpty {
                timelineResponse.arrayValue("posts").mapNotNull { it.asObjectOrNull()?.toPostUi() }
            }

        // First page can be empty on some environments; provide a discover fallback.
        if (timelinePosts.isEmpty() && cursor.isNullOrBlank()) {
            val discoverQuery = mapOf(
                "limit" to limit.toString(),
                "sort" to "latest",
                "q" to "*"
            )
            val discoverResponse = runCatching {
                getAndSyncSession("app.bsky.feed.searchPosts", discoverQuery)
            }.getOrNull()
            if (discoverResponse != null) {
                val discoverPosts = discoverResponse.arrayValue("posts").mapNotNull {
                    it.asObjectOrNull()?.toPostUi()
                }
                if (discoverPosts.isNotEmpty()) {
                    return TimelinePage(
                        posts = discoverPosts,
                        cursor = discoverResponse.stringValue("cursor")
                    )
                }
            }
        }

        return TimelinePage(
            posts = timelinePosts,
            cursor = timelineResponse.stringValue("cursor")
        )
    }

    suspend fun getAuthorFeed(actor: String, limit: Int = 40): TimelinePage {
        val response = getAndSyncSession(
            endpoint = "app.bsky.feed.getAuthorFeed",
            query = mapOf("actor" to actor, "limit" to limit.toString())
        )
        return TimelinePage(
            posts = response.arrayValue("feed").mapNotNull { it.asObjectOrNull()?.toPostUi() },
            cursor = response.stringValue("cursor")
        )
    }

    suspend fun getProfile(actor: String): ActorUi {
        val response = getAndSyncSession(
            endpoint = "app.bsky.actor.getProfile",
            query = mapOf("actor" to actor)
        )
        return response.toActorUi()
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

    suspend fun getFollowers(actor: String, limit: Int = 60): ActorPage {
        val response = getAndSyncSession(
            endpoint = "app.bsky.graph.getFollowers",
            query = mapOf("actor" to actor, "limit" to limit.toString())
        )
        return ActorPage(
            actors = response.arrayValue("followers").mapNotNull { it.asObjectOrNull()?.toActorUi() },
            cursor = response.stringValue("cursor")
        )
    }

    suspend fun getFollows(actor: String, limit: Int = 60): ActorPage {
        val response = getAndSyncSession(
            endpoint = "app.bsky.graph.getFollows",
            query = mapOf("actor" to actor, "limit" to limit.toString())
        )
        return ActorPage(
            actors = response.arrayValue("follows").mapNotNull { it.asObjectOrNull()?.toActorUi() },
            cursor = response.stringValue("cursor")
        )
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
        return createRecord(collection = "app.bsky.feed.post", record = record)
    }

    suspend fun deletePost(postUri: String) {
        deleteRecord(recordUri = postUri, fallbackCollection = "app.bsky.feed.post")
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
        return createRecord(collection = "app.bsky.feed.like", record = record)
    }

    suspend fun unlikePost(likeUri: String) {
        deleteRecord(recordUri = likeUri, fallbackCollection = "app.bsky.feed.like")
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
        return createRecord(collection = "app.bsky.feed.repost", record = record)
    }

    suspend fun unrepost(repostUri: String) {
        deleteRecord(recordUri = repostUri, fallbackCollection = "app.bsky.feed.repost")
    }

    suspend fun follow(actorDid: String): String {
        val record = buildJsonObject {
            put("\$type", "app.bsky.graph.follow")
            put("subject", actorDid)
            put("createdAt", Instant.now().toString())
        }
        return createRecord(collection = "app.bsky.graph.follow", record = record)
    }

    suspend fun unfollow(followUri: String) {
        deleteRecord(recordUri = followUri, fallbackCollection = "app.bsky.graph.follow")
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
}
