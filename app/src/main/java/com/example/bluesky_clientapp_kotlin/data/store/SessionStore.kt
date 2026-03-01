package com.example.bluesky_clientapp_kotlin.data.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bluesky_clientapp_kotlin.data.model.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

private val Context.dataStore by preferencesDataStore(name = "bluesky_session")
private val sessionKey = stringPreferencesKey("auth_session")

class SessionStore(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    val sessionFlow: Flow<AuthSession?> = context.dataStore.data.map { preferences ->
        val raw = preferences[sessionKey] ?: return@map null
        runCatching { json.decodeFromString(AuthSession.serializer(), raw) }.getOrNull()
    }

    suspend fun saveSession(session: AuthSession) {
        context.dataStore.edit { preferences ->
            preferences[sessionKey] = json.encodeToString(AuthSession.serializer(), session)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(sessionKey)
        }
    }
}
