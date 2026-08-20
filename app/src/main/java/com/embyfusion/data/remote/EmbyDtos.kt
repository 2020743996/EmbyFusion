package com.embyfusion.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class AuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String
)

@Serializable data class AuthResponse(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String = "",
    @SerialName("User") val user: UserDto
)

@Serializable data class UserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = ""
)

@Serializable data class ItemsResponse(
    @SerialName("Items") val items: List<ItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0
)

@Serializable data class ItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("Overview") val overview: String = "",
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("RunTimeTicks") val runtimeTicks: Long = 0,
    @SerialName("ProviderIds") val providerIds: Map<String, String> = emptyMap(),
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropTags: List<String> = emptyList(),
    @SerialName("UserData") val userData: UserDataDto? = null,
    @SerialName("MediaSources") val mediaSources: List<MediaSourceDto> = emptyList()
)

@Serializable data class UserDataDto(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false
)

@Serializable data class MediaSourceDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Container") val container: String = "mkv",
    @SerialName("Bitrate") val bitrate: Long = 0,
    @SerialName("Size") val size: Long = 0,
    @SerialName("RunTimeTicks") val runtimeTicks: Long = 0,
    @SerialName("MediaStreams") val streams: List<MediaStreamDto> = emptyList()
)

@Serializable data class MediaStreamDto(
    @SerialName("Index") val index: Int = -1,
    @SerialName("Type") val type: String = "",
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("BitRate") val bitrate: Int? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("ChannelLayout") val channelLayout: String? = null,
    @SerialName("Profile") val profile: String? = null,
    @SerialName("VideoRange") val videoRange: String? = null,
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null
)

@Serializable data class PlaybackReport(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("RunTimeTicks") val runtimeTicks: Long,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("IsMuted") val isMuted: Boolean = false,
    @SerialName("VolumeLevel") val volumeLevel: Int = 100,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
    @SerialName("QueueableMediaTypes") val queueableMediaTypes: List<String> = listOf("Video"),
    @SerialName("PlaylistIndex") val playlistIndex: Int = 0,
    @SerialName("PlaylistLength") val playlistLength: Int = 1,
    @SerialName("PlaybackRate") val playbackRate: Double = 1.0,
    @SerialName("EventName") val eventName: String? = null
)
