package com.example.bluesky_clientapp_kotlin.data.network

import com.example.bluesky_clientapp_kotlin.data.model.ActorUi
import com.example.bluesky_clientapp_kotlin.data.model.NotificationUi
import com.example.bluesky_clientapp_kotlin.data.model.PostMediaType
import com.example.bluesky_clientapp_kotlin.data.model.PostMediaUi
import com.example.bluesky_clientapp_kotlin.data.model.PostUi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.stringValue(key: String): String? {
    val element = this[key] ?: return null
    return element.primitiveOrNull()?.content
}

internal fun JsonObject.intValue(key: String): Int {
    val element = this[key] ?: return 0
    return element.primitiveOrNull()?.intOrNull ?: 0
}

internal fun JsonObject.boolValue(key: String): Boolean {
    val element = this[key] ?: return false
    return element.primitiveOrNull()?.booleanOrNull ?: false
}

internal fun JsonObject.objectValue(key: String): JsonObject? {
    val element = this[key] ?: return null
    return element.asObjectOrNull()
}

internal fun JsonObject.arrayValue(key: String): JsonArray {
    val element = this[key] ?: return JsonArray(emptyList())
    return element.asArrayOrNull() ?: JsonArray(emptyList())
}

internal fun JsonElement.asObjectOrNull(): JsonObject? {
    return runCatching { jsonObject }.getOrNull()
}

internal fun JsonElement.asArrayOrNull(): JsonArray? {
    return runCatching { jsonArray }.getOrNull()
}

internal fun JsonElement.primitiveOrNull(): JsonPrimitive? {
    return runCatching { jsonPrimitive }.getOrNull()
}

internal fun JsonObject.toActorUi(): ActorUi {
    val handle = stringValue("handle").orEmpty()
    val viewer = objectValue("viewer") ?: JsonObject(emptyMap())
    return ActorUi(
        did = stringValue("did").orEmpty(),
        handle = handle,
        displayName = stringValue("displayName").takeUnless { it.isNullOrBlank() } ?: handle,
        avatar = stringValue("avatar"),
        banner = stringValue("banner"),
        description = stringValue("description"),
        viewerFollowingUri = viewer.stringValue("following")
    )
}

internal fun JsonObject.toPostUi(): PostUi {
    val postObject = objectValue("post") ?: this
    val authorObject = postObject.objectValue("author") ?: JsonObject(emptyMap())
    val recordObject = postObject.objectValue("record") ?: JsonObject(emptyMap())
    val viewerObject = postObject.objectValue("viewer") ?: JsonObject(emptyMap())
    val media = buildList {
        postObject.objectValue("embed")?.toPostMediaList()?.let(::addAll)
        postObject.arrayValue("embeds").mapNotNull { it.asObjectOrNull() }.forEach { addAll(it.toPostMediaList()) }
    }.distinctBy { "${it.type}:${it.url}" }
    return PostUi(
        uri = postObject.stringValue("uri").orEmpty(),
        cid = postObject.stringValue("cid").orEmpty(),
        author = authorObject.toActorUi(),
        text = recordObject.stringValue("text").orEmpty(),
        media = media,
        indexedAt = postObject.stringValue("indexedAt").orEmpty(),
        replyCount = postObject.intValue("replyCount"),
        repostCount = postObject.intValue("repostCount"),
        likeCount = postObject.intValue("likeCount"),
        viewerLikeUri = viewerObject.stringValue("like"),
        viewerRepostUri = viewerObject.stringValue("repost")
    )
}

internal fun JsonObject.toNotificationUi(): NotificationUi {
    val authorObject = objectValue("author") ?: JsonObject(emptyMap())
    val subjectObject = objectValue("subject") ?: JsonObject(emptyMap())
    val recordObject = objectValue("record") ?: JsonObject(emptyMap())
    val text = recordObject.stringValue("text").orEmpty()
    return NotificationUi(
        uri = stringValue("uri").orEmpty(),
        reason = stringValue("reason").orEmpty(),
        indexedAt = stringValue("indexedAt").orEmpty(),
        isRead = boolValue("isRead"),
        author = authorObject.toActorUi(),
        text = text,
        relatedPostUri = subjectObject.stringValue("uri"),
        relatedPostCid = subjectObject.stringValue("cid")
    )
}

internal fun JsonElement.prettyContent(): String {
    if (this is JsonNull) return "null"
    return toString()
}

private fun JsonObject.toPostMediaList(): List<PostMediaUi> {
    val type = stringValue("\$type").orEmpty()
    if (type.contains("recordWithMedia", ignoreCase = true)) {
        return objectValue("media")?.toPostMediaList().orEmpty()
    }
    val images = arrayValue("images").mapNotNull { image ->
        val imageObject = image.asObjectOrNull() ?: return@mapNotNull null
        val mediaUrl = imageObject.stringValue("fullsize")
            ?: imageObject.stringValue("url")
            ?: imageObject.stringValue("thumb")
            ?: return@mapNotNull null
        PostMediaUi(
            type = PostMediaType.Image,
            url = mediaUrl,
            thumbnailUrl = imageObject.stringValue("thumb"),
            alt = imageObject.stringValue("alt").orEmpty(),
            aspectRatio = imageObject.aspectRatioOrNull()
        )
    }
    if (images.isNotEmpty()) return images

    val playlistUrl = stringValue("playlist")
    if (playlistUrl.isNullOrBlank()) return emptyList()
    return listOf(
        PostMediaUi(
            type = PostMediaType.Video,
            url = playlistUrl,
            thumbnailUrl = stringValue("thumbnail"),
            alt = stringValue("alt").orEmpty(),
            aspectRatio = aspectRatioOrNull()
        )
    )
}

private fun JsonObject.aspectRatioOrNull(): Float? {
    val ratio = objectValue("aspectRatio") ?: return null
    val width = ratio.floatValue("width") ?: return null
    val height = ratio.floatValue("height") ?: return null
    if (width <= 0f || height <= 0f) return null
    return width / height
}

private fun JsonObject.floatValue(key: String): Float? {
    val primitive = this[key]?.primitiveOrNull() ?: return null
    return primitive.content.toFloatOrNull()
}
