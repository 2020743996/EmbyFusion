package com.embyfusion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.embyfusion.data.EmbyRepository
import com.embyfusion.data.ResumePolicy
import com.embyfusion.data.remote.PlaybackStage
import com.embyfusion.model.AddServerRequest
import com.embyfusion.model.AggregatedMovie
import com.embyfusion.model.EmbyServer
import com.embyfusion.model.SourceVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

data class FusionUiState(
    val servers: List<EmbyServer> = emptyList(),
    val movies: List<AggregatedMovie> = emptyList(),
    val loading: Boolean = true,
    val mutatingServer: Boolean = false,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val query: String = "",
    val selected: AggregatedMovie? = null,
    val player: PlayerRequest? = null
) {
    val filteredMovies: List<AggregatedMovie> get() = if (query.isBlank()) movies else movies.filter {
        it.title.contains(query, true) || it.originalTitle?.contains(query, true) == true ||
            it.year?.toString() == query.trim()
    }
}

data class PlayerCandidate(
    val url: String,
    val serverId: String,
    val playSessionId: String,
    val variant: SourceVariant,
    val transcoding: Boolean = false
)

data class PlayerRequest(
    val movieKey: String,
    val title: String,
    val candidates: List<PlayerCandidate>,
    val candidateIndex: Int = 0,
    val startPositionMs: Long = 0
) {
    val current: PlayerCandidate get() = candidates[candidateIndex]
    val hasFallback: Boolean get() = candidateIndex < candidates.lastIndex
}

class FusionViewModel(private val repository: EmbyRepository) : ViewModel() {
    private val _state = MutableStateFlow(FusionUiState())
    val state: StateFlow<FusionUiState> = _state.asStateFlow()
    private var libraryJob: Job? = null

    init {
        viewModelScope.launch {
            repository.servers.collect { servers ->
                _state.update { it.copy(servers = servers) }
                scheduleLibraryLoad(servers)
            }
        }
    }

    fun refresh() = scheduleLibraryLoad(_state.value.servers)

    fun addServer(request: AddServerRequest, onSuccess: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(mutatingServer = true, error = null) }
        runCatching { repository.addServer(request) }
            .onSuccess { onSuccess() }
            .onFailure { error -> _state.update { it.copy(error = friendly(error)) } }
        _state.update { it.copy(mutatingServer = false) }
    }

    fun removeServer(id: String) = viewModelScope.launch {
        runCatching { repository.removeServer(id) }
            .onFailure { error -> _state.update { it.copy(error = friendly(error)) } }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun select(movie: AggregatedMovie?) = _state.update { it.copy(selected = movie) }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun stopPlayback() = _state.update { it.copy(player = null) }

    fun play(movie: AggregatedMovie, variant: SourceVariant = movie.bestVariant) {
        val ordered = listOf(variant) + movie.variants.filterNot {
            it.serverId == variant.serverId && it.mediaSourceId == variant.mediaSourceId
        }
        val candidates = ordered.mapNotNull { candidate ->
            val server = _state.value.servers.firstOrNull { it.id == candidate.serverId } ?: return@mapNotNull null
            val sessionId = UUID.randomUUID().toString().replace("-", "")
            PlayerCandidate(
                url = repository.streamUrl(server, candidate, sessionId),
                serverId = server.id,
                playSessionId = sessionId,
                variant = candidate
            )
        }.toMutableList()
        if (candidates.isEmpty()) {
            _state.update { it.copy(error = "播放源已被移除，请刷新片库") }
            return
        }
        // Direct-stream every available source first, then use one H.264/AAC HLS transcode as the universal fallback.
        val transcodeSource = candidates.firstOrNull { it.variant.video?.codec.equals("h264", true) } ?: candidates.first()
        val transcodeVariant = transcodeSource.variant
        _state.value.servers.firstOrNull { it.id == transcodeSource.serverId }?.let { server ->
            val sessionId = UUID.randomUUID().toString().replace("-", "")
            candidates += PlayerCandidate(
                url = repository.hlsUrl(server, transcodeVariant, sessionId, movie.resumePositionTicks / 10_000L),
                serverId = server.id,
                playSessionId = sessionId,
                variant = transcodeVariant,
                transcoding = true
            )
        }
        _state.update {
            it.copy(
                player = PlayerRequest(
                    movieKey = movie.key,
                    title = movie.title,
                    candidates = candidates,
                    startPositionMs = movie.resumePositionTicks / 10_000L
                )
            )
        }
    }

    fun playbackStarted(request: PlayerRequest, positionMs: Long) = report(
        request, positionMs, paused = false, stage = PlaybackStage.STARTED
    )

    fun playbackProgress(request: PlayerRequest, positionMs: Long, paused: Boolean, eventName: String) = report(
        request, positionMs, paused, PlaybackStage.PROGRESS, eventName
    )

    fun playbackStopped(request: PlayerRequest, positionMs: Long) {
        report(request, positionMs, paused = true, stage = PlaybackStage.STOPPED)
        if (request.current.transcoding) {
            viewModelScope.launch {
                val server = _state.value.servers.firstOrNull { it.id == request.current.serverId } ?: return@launch
                runCatching { repository.stopTranscoding(server) }
            }
        }
        _state.update { current ->
            val movies = current.movies.map { movie ->
                if (movie.key == request.movieKey) movie.copy(
                    resumePositionTicks = ResumePolicy.normalized(positionMs * 10_000L, movie.runtimeTicks)
                ) else movie
            }
            current.copy(
                movies = movies,
                selected = current.selected?.let { selected -> movies.firstOrNull { it.key == selected.key } }
            )
        }
    }

    fun playbackFailed(request: PlayerRequest, positionMs: Long) {
        val active = _state.value.player ?: return
        if (active.movieKey != request.movieKey || active.candidateIndex != request.candidateIndex) return
        if (active.hasFallback) {
            val nextIndex = active.candidateIndex + 1
            val next = active.candidates[nextIndex]
            val candidates = if (next.transcoding) {
                val server = _state.value.servers.firstOrNull { it.id == next.serverId }
                if (server == null) active.candidates else active.candidates.toMutableList().apply {
                    this[nextIndex] = next.copy(
                        url = repository.hlsUrl(server, next.variant, next.playSessionId, positionMs)
                    )
                }
            } else active.candidates
            _state.update {
                it.copy(
                    player = active.copy(candidates = candidates, candidateIndex = nextIndex, startPositionMs = positionMs)
                )
            }
        } else {
            _state.update { it.copy(player = null, error = "所有可用播放源均无法播放") }
        }
    }

    private fun report(
        request: PlayerRequest,
        positionMs: Long,
        paused: Boolean,
        stage: PlaybackStage,
        eventName: String? = null
    ) = viewModelScope.launch {
        val candidate = request.current
        val server = _state.value.servers.firstOrNull { it.id == candidate.serverId } ?: return@launch
        // Playback telemetry must never interrupt local playback when a server is temporarily unavailable.
        runCatching {
            repository.reportPlayback(
                server, candidate.variant, candidate.playSessionId,
                positionMs, paused, stage,
                playMethod = if (candidate.transcoding) "Transcode" else "DirectStream",
                eventName = eventName
            )
        }
    }

    private fun scheduleLibraryLoad(servers: List<EmbyServer>) {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch { loadLibrary(servers) }
    }

    private suspend fun loadLibrary(servers: List<EmbyServer>) {
        if (servers.isEmpty()) {
            _state.update { it.copy(movies = emptyList(), loading = false, warnings = emptyList()) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        try {
            val result = repository.library(servers)
            _state.update { current ->
                val selected = current.selected?.key?.let { key -> result.movies.firstOrNull { it.key == key } }
                current.copy(movies = result.movies, selected = selected, warnings = result.warnings, loading = false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _state.update { it.copy(loading = false, error = friendly(error)) }
        }
    }

    private fun friendly(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            "401" in message -> "登录失败：请检查用户名、密码或访问令牌"
            "Unable to resolve host" in message -> "无法连接服务器：请检查地址和网络"
            else -> message.ifBlank { "操作失败，请稍后重试" }
        }
    }
}

class FusionViewModelFactory(private val repository: EmbyRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FusionViewModel(repository) as T
}
