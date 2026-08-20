package com.embyfusion.data.remote

import com.embyfusion.model.EmbyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class EmbyApiClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun authenticate(baseUrl: String, username: String, password: String): AuthResponse {
        val body = json.encodeToString(AuthRequest(username, password))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${normalize(baseUrl)}/Users/AuthenticateByName")
            .header("X-Emby-Authorization", authHeader())
            .post(body)
            .build()
        return execute(request)
    }

    suspend fun movies(server: EmbyServer): List<ItemDto> {
        val fields = listOf(
            "ProviderIds", "Overview", "CommunityRating", "RunTimeTicks",
            "MediaSources", "MediaStreams", "OriginalTitle", "UserData"
        ).joinToString(",")
        val movies = ArrayList<ItemDto>()
        var startIndex = 0
        var total = Int.MAX_VALUE
        var pageCount = 0
        while (startIndex < total && pageCount < MAX_MOVIE_PAGES) {
            // Small pages avoid multi-megabyte JSON allocations on large libraries.
            val endpoint = "${normalize(server.baseUrl)}/Users/${server.userId}/Items".toHttpUrl()
                .newBuilder()
                .addQueryParameter("IncludeItemTypes", "Movie")
                .addQueryParameter("Recursive", "true")
                .addQueryParameter("Fields", fields)
                .addQueryParameter("SortBy", "DateCreated,SortName")
                .addQueryParameter("SortOrder", "Descending")
                .addQueryParameter("StartIndex", startIndex.toString())
                .addQueryParameter("Limit", MOVIE_PAGE_SIZE.toString())
                .build()
            val request = Request.Builder().url(endpoint).headers(serverHeaders(server)).get().build()
            val page = execute<ItemsResponse>(request)
            if (page.items.isEmpty()) break
            movies.addAll(page.items)
            startIndex += page.items.size
            total = when {
                page.total > 0 -> page.total.coerceAtLeast(startIndex)
                page.items.size == MOVIE_PAGE_SIZE -> Int.MAX_VALUE
                else -> startIndex
            }
            pageCount++
        }
        return movies
    }

    fun imageUrl(server: EmbyServer, itemId: String, type: String, tag: String?): String? {
        if (tag.isNullOrBlank()) return null
        val base = "${normalize(server.baseUrl)}/Items/$itemId/Images/$type"
        return "$base?tag=$tag&maxWidth=1200&quality=90&api_key=${server.accessToken}"
    }

    fun streamUrl(
        server: EmbyServer,
        itemId: String,
        sourceId: String,
        container: String,
        playSessionId: String
    ): String {
        val safeContainer = container.substringBefore(',').ifBlank { "mkv" }
        return "${normalize(server.baseUrl)}/Videos/$itemId/stream.$safeContainer".toHttpUrl()
            .newBuilder()
            .addQueryParameter("MediaSourceId", sourceId)
            .addQueryParameter("PlaySessionId", playSessionId)
            .addQueryParameter("Static", "true")
            .addQueryParameter("api_key", server.accessToken)
            .build().toString()
    }

    fun hlsUrl(
        server: EmbyServer,
        itemId: String,
        sourceId: String,
        playSessionId: String,
        startPositionTicks: Long
    ): String = "${normalize(server.baseUrl)}/Videos/$itemId/master.m3u8".toHttpUrl()
        .newBuilder()
        .addQueryParameter("MediaSourceId", sourceId)
        .addQueryParameter("DeviceId", DEVICE_ID)
        .addQueryParameter("UserId", server.userId)
        .addQueryParameter("PlaySessionId", playSessionId)
        .addQueryParameter("VideoCodec", "h264")
        .addQueryParameter("AudioCodec", "aac")
        .addQueryParameter("MaxAudioChannels", "6")
        .addQueryParameter("MaxStreamingBitrate", "40000000")
        .addQueryParameter("StartTimeTicks", startPositionTicks.coerceAtLeast(0).toString())
        .addQueryParameter("api_key", server.accessToken)
        .build().toString()

    suspend fun stopActiveEncoding(server: EmbyServer) {
        val url = "${normalize(server.baseUrl)}/Videos/ActiveEncodings".toHttpUrl()
            .newBuilder().addQueryParameter("DeviceId", DEVICE_ID).build()
        val request = Request.Builder().url(url).headers(serverHeaders(server)).delete().build()
        executeUnit(request)
    }

    suspend fun reportPlayback(server: EmbyServer, stage: PlaybackStage, report: PlaybackReport) {
        val endpoint = when (stage) {
            PlaybackStage.STARTED -> "Sessions/Playing"
            PlaybackStage.PROGRESS -> "Sessions/Playing/Progress"
            PlaybackStage.STOPPED -> "Sessions/Playing/Stopped"
        }
        val body = json.encodeToString(report).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${normalize(server.baseUrl)}/$endpoint")
            .headers(serverHeaders(server))
            .post(body)
            .build()
        executeUnit(request)
    }

    private fun serverHeaders(server: EmbyServer) = okhttp3.Headers.Builder()
        .add("X-Emby-Token", server.accessToken)
        .add("X-Emby-Authorization", authHeader(server.userId, server.accessToken))
        .build()

    private fun authHeader(userId: String = "", token: String = "") =
        "Emby UserId=\"$userId\", Client=\"Emby Fusion\", Device=\"Android\", " +
            "DeviceId=\"$DEVICE_ID\", Version=\"0.1.0\", Token=\"$token\""

    private fun normalize(value: String): String {
        var result = value.trim().trimEnd('/')
        if (!result.startsWith("http://") && !result.startsWith("https://")) result = "https://$result"
        return result
    }

    private suspend inline fun <reified T> execute(request: Request): T = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Emby ${response.code}: ${text.take(240)}")
            json.decodeFromString<T>(text)
        }
    }


    private suspend fun executeUnit(request: Request) = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                error("Emby ${response.code}: ${text.take(240)}")
            }
        }
    }

    private companion object {
        const val DEVICE_ID = "emby-fusion-android"
        const val MOVIE_PAGE_SIZE = 250
        const val MAX_MOVIE_PAGES = 200
    }
}

enum class PlaybackStage { STARTED, PROGRESS, STOPPED }
