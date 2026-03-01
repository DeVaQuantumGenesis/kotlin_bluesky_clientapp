package com.example.bluesky_clientapp_kotlin.data.network

import com.example.bluesky_clientapp_kotlin.data.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ATProtoKitClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }
) {
    private val refreshMutex = Mutex()

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Volatile
    private var session: AuthSession? = null

    fun setSession(value: AuthSession?) {
        session = value
    }

    fun currentSession(): AuthSession? = session

    fun close() {
        httpClient.close()
    }

    suspend fun createSession(identifier: String, appPassword: String): AuthSession {
        val payload = buildJsonObject {
            put("identifier", identifier)
            put("password", appPassword)
        }
        val response = requestJson(
            endpoint = "com.atproto.server.createSession",
            method = HttpMethod.Post,
            body = payload,
            requiresAuth = false,
            retryOnAuth = false
        )
        return extractSession(response, fallback = null).also { setSession(it) }
    }

    suspend fun refreshSession(): AuthSession {
        return refreshSessionInternal()
    }

    suspend fun get(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        requiresAuth: Boolean = true
    ): JsonObject {
        return requestJson(
            endpoint = endpoint,
            method = HttpMethod.Get,
            query = query,
            requiresAuth = requiresAuth
        )
    }

    suspend fun post(
        endpoint: String,
        body: JsonElement = JsonObject(emptyMap()),
        requiresAuth: Boolean = true
    ): JsonObject {
        return requestJson(
            endpoint = endpoint,
            method = HttpMethod.Post,
            body = body,
            requiresAuth = requiresAuth
        )
    }

    suspend fun uploadBlob(bytes: ByteArray, mimeType: String): JsonObject {
        require(bytes.isNotEmpty()) { "Blob payload is empty" }
        return requestBlob(
            endpoint = "com.atproto.repo.uploadBlob",
            bytes = bytes,
            mimeType = mimeType,
            requiresAuth = true
        )
    }

    private suspend fun refreshSessionInternal(): AuthSession {
        return refreshMutex.withLock {
            val response = requestJson(
                endpoint = "com.atproto.server.refreshSession",
                method = HttpMethod.Post,
                body = JsonObject(emptyMap()),
                requiresAuth = true,
                useRefreshToken = true,
                retryOnAuth = false
            )
            val refreshed = extractSession(response, fallback = session)
            setSession(refreshed)
            refreshed
        }
    }

    private suspend fun requestJson(
        endpoint: String,
        method: HttpMethod,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        requiresAuth: Boolean = true,
        useRefreshToken: Boolean = false,
        retryOnAuth: Boolean = true
    ): JsonObject {
        val activeSession = session
        val response = httpClient.request("${baseUrl(activeSession)}/xrpc/$endpoint") {
            this.method = method
            accept(ContentType.Application.Json)
            if (method != HttpMethod.Get) {
                contentType(ContentType.Application.Json)
            }
            query.forEach { (key, value) ->
                parameter(key, value)
            }
            if (requiresAuth) {
                val token = if (useRefreshToken) activeSession?.refreshJwt else activeSession?.accessJwt
                require(!token.isNullOrBlank()) { "Not authenticated" }
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (method != HttpMethod.Get && body != null) {
                setBody(body)
            }
        }

        val responseText = response.bodyAsText()
        if (
            requiresAuth &&
            !useRefreshToken &&
            retryOnAuth &&
            isAuthRetryableFailure(response.status, responseText)
        ) {
            runCatching { refreshSessionInternal() }.getOrElse {
                setSession(null)
                throw IllegalStateException("Session expired. Please login again.")
            }
            return requestJson(
                endpoint = endpoint,
                method = method,
                query = query,
                body = body,
                requiresAuth = requiresAuth,
                useRefreshToken = false,
                retryOnAuth = false
            )
        }
        return parseJsonResponse(endpoint = endpoint, statusCode = response.status.value, responseText = responseText)
    }

    private suspend fun requestBlob(
        endpoint: String,
        bytes: ByteArray,
        mimeType: String,
        requiresAuth: Boolean = true,
        useRefreshToken: Boolean = false,
        retryOnAuth: Boolean = true
    ): JsonObject {
        val activeSession = session
        val contentType = runCatching { ContentType.parse(mimeType) }
            .getOrDefault(ContentType.Application.OctetStream)
        val response = httpClient.request("${baseUrl(activeSession)}/xrpc/$endpoint") {
            method = HttpMethod.Post
            accept(ContentType.Application.Json)
            contentType(contentType)
            if (requiresAuth) {
                val token = if (useRefreshToken) activeSession?.refreshJwt else activeSession?.accessJwt
                require(!token.isNullOrBlank()) { "Not authenticated" }
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(bytes)
        }
        val responseText = response.bodyAsText()
        if (
            requiresAuth &&
            !useRefreshToken &&
            retryOnAuth &&
            isAuthRetryableFailure(response.status, responseText)
        ) {
            runCatching { refreshSessionInternal() }.getOrElse {
                setSession(null)
                throw IllegalStateException("Session expired. Please login again.")
            }
            return requestBlob(
                endpoint = endpoint,
                bytes = bytes,
                mimeType = mimeType,
                requiresAuth = requiresAuth,
                useRefreshToken = false,
                retryOnAuth = false
            )
        }
        return parseJsonResponse(endpoint = endpoint, statusCode = response.status.value, responseText = responseText)
    }

    private fun parseJsonResponse(endpoint: String, statusCode: Int, responseText: String): JsonObject {
        if (statusCode !in 200..299) {
            throw IllegalStateException("XRPC $endpoint failed: HTTP $statusCode $responseText")
        }
        if (responseText.isBlank()) {
            return JsonObject(emptyMap())
        }
        val parsed = json.parseToJsonElement(responseText)
        return parsed.asObjectOrNull() ?: buildJsonObject { put("value", parsed) }
    }

    private fun isAuthRetryableFailure(status: HttpStatusCode, responseText: String): Boolean {
        if (status == HttpStatusCode.Unauthorized) return true
        if (status != HttpStatusCode.BadRequest) return false
        val payload = runCatching {
            json.parseToJsonElement(responseText).asObjectOrNull()
        }.getOrNull()
        val errorCode = payload?.stringValue("error").orEmpty()
        val message = payload?.stringValue("message").orEmpty()
        return errorCode.equals("ExpiredToken", ignoreCase = true) ||
            errorCode.equals("InvalidToken", ignoreCase = true) ||
            message.contains("token has expired", ignoreCase = true) ||
            message.contains("expired token", ignoreCase = true)
    }

    private fun extractSession(source: JsonObject, fallback: AuthSession?): AuthSession {
        val did = source.stringValue("did") ?: fallback?.did
        val handle = source.stringValue("handle") ?: fallback?.handle
        val accessJwt = source.stringValue("accessJwt") ?: fallback?.accessJwt
        val refreshJwt = source.stringValue("refreshJwt") ?: fallback?.refreshJwt
        require(!did.isNullOrBlank()) { "Missing did in session response" }
        require(!handle.isNullOrBlank()) { "Missing handle in session response" }
        require(!accessJwt.isNullOrBlank()) { "Missing access token" }
        require(!refreshJwt.isNullOrBlank()) { "Missing refresh token" }
        return AuthSession(
            did = did,
            handle = handle,
            accessJwt = accessJwt,
            refreshJwt = refreshJwt,
            serviceEndpoint = extractServiceEndpoint(source, fallback)
        )
    }

    private fun extractServiceEndpoint(source: JsonObject, fallback: AuthSession?): String {
        source.stringValue("serviceEndpoint")?.takeIf { it.isNotBlank() }?.let { return it }
        val fromDidDoc = source.objectValue("didDoc")
            ?.arrayValue("service")
            ?.firstNotNullOfOrNull { it.asObjectOrNull()?.stringValue("serviceEndpoint") }
            ?.takeIf { it.isNotBlank() }
        return fromDidDoc ?: fallback?.serviceEndpoint ?: DEFAULT_SERVICE
    }

    private fun baseUrl(session: AuthSession?): String {
        return session?.serviceEndpoint?.removeSuffix("/") ?: DEFAULT_SERVICE
    }

    companion object {
        private const val DEFAULT_SERVICE = "https://bsky.social"
    }
}
