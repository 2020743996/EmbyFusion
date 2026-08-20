package com.embyfusion.data

import com.embyfusion.model.MediaStream

object QualityScorer {
    fun score(streams: List<MediaStream>, totalBitrate: Long): Int {
        val video = streams.firstOrNull { it.type.equals("Video", true) }
        val audio = streams.firstOrNull { it.type.equals("Audio", true) }
        val height = video?.height ?: 0
        val resolution = when {
            height >= 4320 -> 700
            height >= 2160 -> 600
            height >= 1440 -> 480
            height >= 1080 -> 400
            height >= 720 -> 260
            else -> 100
        }
        val range = listOfNotNull(video?.videoRange, video?.videoRangeType, video?.profile).joinToString(" ").lowercase()
        val hdr = when {
            "dolby" in range || "dovi" in range || "dvhe" in range -> 90
            "hdr10+" in range || "hdr10plus" in range -> 75
            "hdr" in range || "pq" in range -> 60
            else -> 0
        }
        val codec = when (video?.codec?.lowercase()) {
            "av1" -> 35
            "hevc", "h265" -> 30
            "vp9" -> 20
            "h264", "avc" -> 15
            else -> 0
        }
        val bitrate = (totalBitrate / 1_000_000L).coerceAtMost(120).toInt()
        val audioCodec = when (audio?.codec?.lowercase()) {
            "truehd" -> 35
            "dts", "dts-hd", "dtsma" -> 30
            "flac", "pcm", "eac3" -> 20
            "ac3" -> 12
            else -> 5
        }
        val channels = ((audio?.channels ?: 2) * 3).coerceAtMost(24)
        return resolution + hdr + codec + bitrate + audioCodec + channels
    }

    fun badge(stream: MediaStream?): String = when {
        (stream?.height ?: 0) >= 4320 -> "8K"
        (stream?.height ?: 0) >= 2160 -> "4K"
        (stream?.height ?: 0) >= 1440 -> "1440P"
        (stream?.height ?: 0) >= 1080 -> "1080P"
        (stream?.height ?: 0) >= 720 -> "720P"
        else -> "SD"
    }

    fun hdrBadge(stream: MediaStream?): String? {
        val value = listOfNotNull(stream?.videoRange, stream?.videoRangeType, stream?.profile).joinToString(" ").lowercase()
        return when {
            "dolby" in value || "dovi" in value || "dvhe" in value -> "DOLBY VISION"
            "hdr10+" in value || "hdr10plus" in value -> "HDR10+"
            "hdr" in value || "pq" in value -> "HDR"
            else -> null
        }
    }
}
