package com.embyfusion.data

import com.embyfusion.model.MediaStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityScorerTest {
    @Test fun `dolby vision 4k outranks high bitrate 1080p`() {
        val uhd = listOf(video(2160, "hevc", "DOVI"), audio("truehd", 8))
        val fullHd = listOf(video(1080, "h264", "SDR"), audio("aac", 6))
        assertTrue(QualityScorer.score(uhd, 35_000_000) > QualityScorer.score(fullHd, 60_000_000))
    }

    @Test fun `range badges identify dolby vision`() {
        assertEquals("DOLBY VISION", QualityScorer.hdrBadge(video(2160, "hevc", "DolbyVision")))
    }

    private fun video(height: Int, codec: String, range: String) = MediaStream(
        0, "Video", codec, 3840, height, null, null, null, range, range, range, null, null
    )
    private fun audio(codec: String, channels: Int) = MediaStream(
        1, "Audio", codec, null, null, null, channels, null, null, null, null, "chi", null
    )
}
