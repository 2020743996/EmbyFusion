package com.embyfusion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.embyfusion.data.QualityScorer
import com.embyfusion.ui.theme.FusionGreen
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable fun PlayerScreen(
    request: PlayerRequest,
    onBack: () -> Unit,
    onStarted: (positionMs: Long) -> Unit,
    onProgress: (positionMs: Long, paused: Boolean, eventName: String) -> Unit,
    onStopped: (positionMs: Long) -> Unit,
    onFailure: (positionMs: Long) -> Unit
) {
    val context = LocalContext.current
    val candidate = request.current
    val positionOffsetMs = if (candidate.transcoding) request.startPositionMs else 0L
    var started by remember(candidate.playSessionId) { mutableStateOf(false) }
    var failed by remember(candidate.playSessionId) { mutableStateOf(false) }
    val player = remember(candidate.playSessionId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(candidate.url))
            if (request.startPositionMs > 0 && !candidate.transcoding) seekTo(request.startPositionMs)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !started) {
                    started = true
                    onStarted(player.currentPosition.reportedPosition(positionOffsetMs))
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (started && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    onProgress(
                        player.currentPosition.reportedPosition(positionOffsetMs),
                        !playWhenReady,
                        if (playWhenReady) "Unpause" else "Pause"
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!failed) {
                    failed = true
                    onFailure(player.currentPosition.reportedPosition(positionOffsetMs))
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val position = player.currentPosition.reportedPosition(positionOffsetMs)
            player.removeListener(listener)
            if (started) onStopped(position)
            player.release()
        }
    }

    LaunchedEffect(player, started) {
        if (!started) return@LaunchedEffect
        while (true) {
            delay(10_000)
            onProgress(
                player.currentPosition.reportedPosition(positionOffsetMs),
                !player.playWhenReady,
                "TimeUpdate"
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3_500
                    setShowSubtitleButton(true)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.player = player }
        )
        Row(
            Modifier.fillMaxWidth().padding(WindowInsets.statusBars.asPaddingValues()).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onBack, Modifier.background(Color.Black.copy(.55f), RoundedCornerShape(50))) {
                Icon(Icons.Default.ArrowBack, "退出播放")
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(request.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${candidate.variant.serverName} · ${QualityScorer.badge(candidate.variant.video)}" +
                        when {
                            candidate.transcoding -> " · HLS 兼容转码"
                            request.candidateIndex > 0 -> " · 备用源 ${request.candidateIndex + 1}/${request.candidates.size}"
                            else -> ""
                        },
                    color = FusionGreen,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            if (candidate.variant.streams.any { it.type.equals("Subtitle", true) }) {
                Text("CC", color = Color.White.copy(.75f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

private fun Long.reportedPosition(offsetMs: Long): Long = coerceAtLeast(0L) + offsetMs.coerceAtLeast(0L)
