package com.example.bluesky_clientapp_kotlin.data.network

import com.example.bluesky_clientapp_kotlin.data.model.AuthMethod
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
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class OAuthAuthorizationRequest(
    val authorizationUrl: String,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val clientId: String
)

object BlueskyOAuthConfig {
    const val LOOPBACK_HOST = "127.0.0.1"
    const val LOOPBACK_REDIRECT_PATH = "/oauth/callback"
    const val SCOPE = "atproto transition:generic"
    const val PAR_ENDPOINT = "https://bsky.social/oauth/par"
    const val AUTHORIZATION_ENDPOINT = "https://bsky.social/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://bsky.social/oauth/token"

    fun redirectUriForPort(port: Int): String {
        return "http://$LOOPBACK_HOST:$port$LOOPBACK_REDIRECT_PATH"
    }

    fun clientIdForRedirectUri(redirectUri: String): String {
        val encodedRedirect = redirectUri.encodeURLParameter()
        val encodedScope = SCOPE.encodeURLParameter()
        return "http://localhost?redirect_uri=$encodedRedirect&scope=$encodedScope"
    }

    fun isLoopbackRedirectUri(value: String): Boolean {
        return value.startsWith("http://$LOOPBACK_HOST:", ignoreCase = true) &&
            value.contains(LOOPBACK_REDIRECT_PATH)
    }
}

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

    suspend fun createOAuthAuthorizationRequest(redirectUri: String): OAuthAuthorizationRequest {
        require(BlueskyOAuthConfig.isLoopbackRedirectUri(redirectUri)) {
            "Invalid OAuth redirect URI. Use a 127.0.0.1 loopback redirect URI."
        }
        val state = randomUrlSafeValue(24)
        val verifier = randomUrlSafeValue(64)
        val challenge = createCodeChallenge(verifier)
        val clientId = BlueskyOAuthConfig.clientIdForRedirectUri(redirectUri)
        val requestUri = createPushedAuthorizationRequest(
            state = state,
            codeChallenge = challenge,
            redirectUri = redirectUri,
            clientId = clientId
        )
        val authorizationUrl = buildString {
            append(BlueskyOAuthConfig.AUTHORIZATION_ENDPOINT)
            append("?client_id=${clientId.encodeURLParameter()}")
            append("&request_uri=${requestUri.encodeURLParameter()}")
        }
        return OAuthAuthorizationRequest(
            authorizationUrl = authorizationUrl,
            state = state,
            codeVerifier = verifier,
            redirectUri = redirectUri,
            clientId = clientId
        )
    }

    private suspend fun createPushedAuthorizationRequest(
        state: String,
        codeChallenge: String,
        redirectUri: String,
        clientId: String
    ): String {
        val formParams = mapOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "scope" to BlueskyOAuthConfig.SCOPE,
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        )
        val encodedBody = formParams.entries.joinToString("&") { (key, value) ->
            "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
        }
        val response = httpClient.request(BlueskyOAuthConfig.PAR_ENDPOINT) {
            method = HttpMethod.Post
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(encodedBody)
        }
        val responseText = response.bodyAsText()
        val payload = parseJsonResponse(
            endpoint = "oauth/par",
            statusCode = response.status.value,
            responseText = responseText
        )
        return payload.stringValue("request_uri")
            ?: throw IllegalStateException("PAR response did not include request_uri.")
    }

    suspend fun createOAuthSession(
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String
    ): AuthSession {
        val tokenResponse = requestOAuthToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to codeVerifier
            )
        )
        val accessToken = tokenResponse.stringValue("access_token")
            ?: throw IllegalStateException("OAuth token response did not include an access token.")
        val refreshToken = tokenResponse.stringValue("refresh_token")
            ?: throw IllegalStateException("OAuth token response did not include a refresh token.")
        val did = tokenResponse.stringValue("sub")
            ?: tokenResponse.stringValue("did")
            ?: "oauth:pending"
        val provisionalSession = AuthSession(
            did = did,
            handle = did,
            accessJwt = accessToken,
            refreshJwt = refreshToken,
            serviceEndpoint = DEFAULT_SERVICE,
            authMethod = AuthMethod.OAuth,
            oauthClientId = clientId,
            oauthRedirectUri = redirectUri
        )
        setSession(provisionalSession)
        return enrichOAuthIdentity(provisionalSession).also { setSession(it) }
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
            val activeSession = session ?: throw IllegalStateException("Not authenticated")
            val refreshed = when (activeSession.authMethod) {
                AuthMethod.OAuth -> refreshOAuthSession(activeSession)
                AuthMethod.LegacyAppPassword -> refreshLegacySession(activeSession)
            }
            setSession(refreshed)
            refreshed
        }
    }

    private suspend fun refreshLegacySession(activeSession: AuthSession): AuthSession {
        val response = requestJson(
            endpoint = "com.atproto.server.refreshSession",
            method = HttpMethod.Post,
            body = JsonObject(emptyMap()),
            requiresAuth = true,
            useRefreshToken = true,
            retryOnAuth = false
        )
        return extractSession(response, fallback = activeSession)
    }

    private suspend fun refreshOAuthSession(activeSession: AuthSession): AuthSession {
        val clientId = activeSession.oauthClientId
            ?: throw IllegalStateException("OAuth client_id is missing from saved session.")
        val tokenResponse = requestOAuthToken(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to activeSession.refreshJwt,
                "client_id" to clientId
            )
        )
        val accessToken = tokenResponse.stringValue("access_token")
            ?: throw IllegalStateException("OAuth refresh response did not include an access token.")
        val refreshed = activeSession.copy(
            accessJwt = accessToken,
            refreshJwt = tokenResponse.stringValue("refresh_token") ?: activeSession.refreshJwt
        )
        setSession(refreshed)
        return enrichOAuthIdentity(refreshed)
    }

    private suspend fun requestOAuthToken(formParams: Map<String, String>): JsonObject {
        val encodedBody = formParams.entries.joinToString("&") { (key, value) ->
            "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
        }
        val response = httpClient.request(BlueskyOAuthConfig.TOKEN_ENDPOINT) {
            method = HttpMethod.Post
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(encodedBody)
        }
        val responseText = response.bodyAsText()
        return parseJsonResponse(
            endpoint = "oauth/token",
            statusCode = response.status.value,
            responseText = responseText
        )
    }

    private suspend fun enrichOAuthIdentity(source: AuthSession): AuthSession {
        val sessionResponse = runCatching {
            requestJson(
                endpoint = "com.atproto.server.getSession",
                method = HttpMethod.Get,
                requiresAuth = true,
                retryOnAuth = false
            )
        }.getOrNull()

        val profileResponse = runCatching {
            val actor = (sessionResponse?.stringValue("did") ?: source.did).takeIf { it.startsWith("did:") }
                ?: return@runCatching null
            requestJson(
                endpoint = "app.bsky.actor.getProfile",
                method = HttpMethod.Get,
                query = mapOf("actor" to actor),
                requiresAuth = true,
                retryOnAuth = false
            )
        }.getOrNull()

        val did = sessionResponse?.stringValue("did")
            ?: profileResponse?.stringValue("did")
            ?: source.did
        val handle = sessionResponse?.stringValue("handle")
            ?: profileResponse?.stringValue("handle")
            ?: source.handle
        require(did.isNotBlank() && did != "oauth:pending") { "OAuth login completed but did could not be resolved." }
        require(handle.isNotBlank() && handle != "oauth:pending") { "OAuth login completed but handle could not be resolved." }

        return source.copy(
            did = did,
            handle = handle,
            serviceEndpoint = extractServiceEndpoint(
                source = sessionResponse ?: JsonObject(emptyMap()),
                fallback = source
            ),
            authMethod = AuthMethod.OAuth
        )
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
                val token = if (useRefreshToken && activeSession?.authMethod == AuthMethod.LegacyAppPassword) {
                    activeSession.refreshJwt
                } else {
                    activeSession?.accessJwt
                }
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
                val token = if (useRefreshToken && activeSession?.authMethod == AuthMethod.LegacyAppPassword) {
                    activeSession.refreshJwt
                } else {
                    activeSession?.accessJwt
                }
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
            serviceEndpoint = extractServiceEndpoint(source, fallback),
            authMethod = AuthMethod.LegacyAppPassword
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

    private fun randomUrlSafeValue(size: Int): String {
        val randomBytes = ByteArray(size)
        secureRandom.nextBytes(randomBytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes)
    }

    private fun createCodeChallenge(codeVerifier: String): String {
        val hashed = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed)
    }

    companion object {
        private const val DEFAULT_SERVICE = "https://bsky.social"
        private val secureRandom = SecureRandom()
    }
}
