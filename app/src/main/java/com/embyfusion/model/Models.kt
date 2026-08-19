package com.embyfusion.model

data class EmbyServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val userId: String,
    val accessToken: String
)

data class MediaStream(
    val index: Int,
    val type: String,
    val codec: String?,
    val width: Int?,
    val height: Int?,
    val bitRate: Int?,
    val channels: Int?,
    val channelLayout: String?,
    val profile: String?,
    val videoRange: String?,
    val videoRangeType: String?,
    val language: String?,
    val displayTitle: String?
)

data class SourceVariant(
    val serverId: String,
    val serverName: String,
    val itemId: String,
    val mediaSourceId: String,
    val container: String,
    val name: String,
    val totalBitrate: Long,
    val sizeBytes: Long,
    val runtimeTicks: Long,
    val streams: List<MediaStream>,
    val score: Int
) {
    val video: MediaStream? get() = streams.firstOrNull { it.type.equals("Video", true) }
    val audio: MediaStream? get() = streams.firstOrNull { it.type.equals("Audio", true) }
}

data class AggregatedMovie(
    val key: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String,
    val communityRating: Double?,
    val runtimeTicks: Long,
    val posterUrl: String?,
    val backdropUrl: String?,
    val providerIds: Map<String, String>,
    val variants: List<SourceVariant>,
    val resumePositionTicks: Long
) {
    val bestVariant: SourceVariant get() = variants.maxBy { it.score }
}

data class AddServerRequest(
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val password: String
)
