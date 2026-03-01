package com.example.bluesky_clientapp_kotlin.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthMethod {
    LegacyAppPassword,
    OAuth
}

@Serializable
data class AuthSession(
    val did: String,
    val handle: String,
    val accessJwt: String,
    val refreshJwt: String,
    val serviceEndpoint: String = "https://bsky.social",
    val authMethod: AuthMethod = AuthMethod.LegacyAppPassword,
    val oauthClientId: String? = null,
    val oauthRedirectUri: String? = null
)

data class ActorUi(
    val did: String,
    val handle: String,
    val displayName: String,
    val avatar: String?,
    val banner: String?,
    val description: String?,
    val viewerFollowingUri: String?
)

data class PostUi(
    val uri: String,
    val cid: String,
    val author: ActorUi,
    val text: String,
    val media: List<PostMediaUi>,
    val indexedAt: String,
    val replyCount: Int,
    val repostCount: Int,
    val likeCount: Int,
    val viewerLikeUri: String?,
    val viewerRepostUri: String?
)

enum class PostMediaType {
    Image,
    Video
}

data class PostMediaUi(
    val type: PostMediaType,
    val url: String,
    val thumbnailUrl: String?,
    val alt: String,
    val aspectRatio: Float?
)

enum class DraftMediaType {
    Image,
    Video
}

data class DraftMediaAttachment(
    val type: DraftMediaType,
    val mimeType: String,
    val bytes: ByteArray,
    val alt: String = ""
)

data class NotificationUi(
    val uri: String,
    val reason: String,
    val indexedAt: String,
    val isRead: Boolean,
    val author: ActorUi,
    val text: String,
    val relatedPostUri: String?,
    val relatedPostCid: String?
)

data class TimelinePage(
    val posts: List<PostUi>,
    val cursor: String?
)

data class ActorPage(
    val actors: List<ActorUi>,
    val cursor: String?
)

data class NotificationPage(
    val notifications: List<NotificationUi>,
    val cursor: String?
)

data class PostThreadUi(
    val root: PostUi,
    val replies: List<PostUi>
)
