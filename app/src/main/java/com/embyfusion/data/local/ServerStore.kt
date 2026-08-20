package com.embyfusion.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.embyfusion.model.EmbyServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("servers")

class ServerStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("configured_servers")
    private val cipher = TokenCipher()

    val servers: Flow<List<EmbyServer>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString<List<StoredServer>>(raw) }.getOrDefault(emptyList())
        }.orEmpty().mapNotNull { runCatching { it.toModel(cipher) }.getOrNull() }
    }

    suspend fun save(server: EmbyServer) = context.dataStore.edit { prefs ->
        val current = decode(prefs).filterNot { it.id == server.id }
        prefs[key] = json.encodeToString(current + StoredServer.from(server, cipher))
    }

    suspend fun remove(id: String) = context.dataStore.edit { prefs ->
        prefs[key] = json.encodeToString(decode(prefs).filterNot { it.id == id })
    }

    private fun decode(prefs: Preferences): List<StoredServer> = prefs[key]?.let {
        runCatching { json.decodeFromString<List<StoredServer>>(it) }.getOrDefault(emptyList())
    }.orEmpty()
}

@Serializable private data class StoredServer(
    val id: String, val name: String, val baseUrl: String, val userId: String, val accessToken: String
) {
    fun toModel(cipher: TokenCipher) = EmbyServer(id, name, baseUrl, userId, cipher.decrypt(accessToken))
    companion object {
        fun from(value: EmbyServer, cipher: TokenCipher) = StoredServer(
            value.id, value.name, value.baseUrl, value.userId, cipher.encrypt(value.accessToken)
        )
    }
}
