package com.embyfusion.data

import com.embyfusion.data.local.ServerStore
import com.embyfusion.data.remote.EmbyApiClient
import com.embyfusion.data.remote.ItemDto
import com.embyfusion.data.remote.PlaybackReport
import com.embyfusion.data.remote.PlaybackStage
import com.embyfusion.model.AddServerRequest
import com.embyfusion.model.AggregatedMovie
import com.embyfusion.model.EmbyServer
import com.embyfusion.model.MediaStream
import com.embyfusion.model.SourceVariant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

class EmbyRepository(private val store: ServerStore, private val api: EmbyApiClient) {
    val servers: Flow<List<EmbyServer>> = store.servers

    suspend fun addServer(request: AddServerRequest) {
        val url = normalize(request.baseUrl)
        val auth = api.authenticate(url, request.username, request.password)
        store.save(
            EmbyServer(
                id = auth.serverId.ifBlank { UUID.randomUUID().toString() },
                name = request.displayName.ifBlank { auth.user.name.ifBlank { url } },
                baseUrl = url,
                userId = auth.user.id,
                accessToken = auth.accessToken
            )
        )
    }

    suspend fun removeServer(id: String) = store.remove(id)

    suspend fun library(servers: List<EmbyServer>): LibraryResult = supervisorScope {
        val results = servers.map { server ->
            async(Dispatchers.IO) {
                try {
                    Result.success(withTimeout(SERVER_LOAD_TIMEOUT_MS) { server to api.movies(server) })
                } catch (error: TimeoutCancellationException) {
                    Result.failure(IllegalStateException("${server.name} 读取片库超时"))
                }
            }
        }.awaitAll()
        val failures = results.mapNotNull { it.exceptionOrNull()?.message }
        val available = results.mapNotNull { it.getOrNull() }
        val movies = withContext(Dispatchers.Default) { merge(available) }
        LibraryResult(movies, failures)
    }

    fun streamUrl(server: EmbyServer, variant: SourceVariant, playSessionId: String): String =
        api.streamUrl(server, variant.itemId, variant.mediaSourceId, variant.container, playSessionId)

    fun hlsUrl(
        server: EmbyServer,
        variant: SourceVariant,
        playSessionId: String,
        startPositionMs: Long
    ): String = api.hlsUrl(
        server, variant.itemId, variant.mediaSourceId, playSessionId,
        startPositionMs.coerceAtLeast(0) * 10_000L
    )

    suspend fun reportPlayback(
        server: EmbyServer,
        variant: SourceVariant,
        playSessionId: String,
        positionMs: Long,
        paused: Boolean,
        stage: PlaybackStage,
        playMethod: String,
        eventName: String? = null
    ) = api.reportPlayback(
        server,
        stage,
        PlaybackReport(
            itemId = variant.itemId,
            mediaSourceId = variant.mediaSourceId,
            playSessionId = playSessionId,
            positionTicks = positionMs.coerceAtLeast(0) * 10_000L,
            runtimeTicks = variant.runtimeTicks,
            isPaused = paused,
            playMethod = playMethod,
            eventName = eventName
        )
    )

    suspend fun stopTranscoding(server: EmbyServer) = api.stopActiveEncoding(server)

    private fun merge(all: List<Pair<EmbyServer, List<ItemDto>>>): List<AggregatedMovie> {
        val buckets = linkedMapOf<String, MutableList<Pair<EmbyServer, ItemDto>>>()
        val fallbackIndex = mutableMapOf<String, MutableList<String>>()
        all.forEach { (server, items) -> items.forEach { item ->
            val providerKey = MovieMatcher.key(item)
            if (!providerKey.startsWith("fallback:")) {
                buckets.getOrPut(providerKey) { mutableListOf() } += server to item
            } else {
                val title = MovieMatcher.fallbackTitle(item)
                val candidateKeys = fallbackIndex.getOrPut(title) { mutableListOf() }
                val matchingKey = candidateKeys.firstOrNull { key ->
                    buckets[key]?.firstOrNull()?.second?.let { MovieMatcher.sameFallback(it, item) } == true
                }
                val targetKey = matchingKey ?: uniqueFallbackKey(providerKey, buckets).also(candidateKeys::add)
                buckets.getOrPut(targetKey) { mutableListOf() } += server to item
            }
        } }
        return buckets.map { (key, entries) -> aggregate(key, entries) }
            .filter { it.variants.isNotEmpty() }
            .sortedByDescending { it.bestVariant.score }
    }

    private fun uniqueFallbackKey(
        preferred: String,
        buckets: Map<String, MutableList<Pair<EmbyServer, ItemDto>>>
    ): String {
        if (preferred !in buckets) return preferred
        var suffix = 2
        while ("$preferred#$suffix" in buckets) suffix++
        return "$preferred#$suffix"
    }

    private fun aggregate(key: String, entries: List<Pair<EmbyServer, ItemDto>>): AggregatedMovie {
        val representative = entries.maxBy { it.second.overview.length }
        val (server, item) = representative
        val variants = entries.flatMap { (origin, movie) -> movie.mediaSources.map { source ->
            val streams = source.streams.map { stream ->
                MediaStream(stream.index, stream.type, stream.codec, stream.width, stream.height, stream.bitrate,
                    stream.channels, stream.channelLayout, stream.profile, stream.videoRange,
                    stream.videoRangeType, stream.language, stream.displayTitle)
            }
            SourceVariant(origin.id, origin.name, movie.id, source.id, source.container,
                source.name.ifBlank { movie.name }, source.bitrate, source.size,
                source.runtimeTicks.takeIf { it > 0 } ?: movie.runtimeTicks, streams,
                QualityScorer.score(streams, source.bitrate))
        } }.sortedByDescending { it.score }
        val runtime = item.runtimeTicks.takeIf { it > 0 } ?: variants.maxOfOrNull { it.runtimeTicks } ?: 0
        val resume = ResumePolicy.normalized(
            entries.maxOfOrNull { it.second.userData?.playbackPositionTicks ?: 0L } ?: 0L,
            runtime
        )
        return AggregatedMovie(
            key, item.name, item.originalTitle, item.year, item.overview, item.communityRating,
            runtime, api.imageUrl(server, item.id, "Primary", item.imageTags["Primary"]),
            api.imageUrl(server, item.id, "Backdrop", item.backdropTags.firstOrNull()),
            item.providerIds, variants, resume
        )
    }

    private fun normalize(value: String): String {
        var result = value.trim().trimEnd('/')
        if (!result.startsWith("http://") && !result.startsWith("https://")) result = "https://$result"
        return result
    }

    private companion object { const val SERVER_LOAD_TIMEOUT_MS = 120_000L }
}

data class LibraryResult(val movies: List<AggregatedMovie>, val warnings: List<String>)
