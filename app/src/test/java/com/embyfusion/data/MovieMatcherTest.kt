package com.embyfusion.data

import com.embyfusion.data.remote.ItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieMatcherTest {
    @Test fun `provider id is stable across translated titles`() {
        val a = movie("银翼杀手", 1982, mapOf("Tmdb" to "78"))
        val b = movie("Blade Runner", 1982, mapOf("TMDB" to "78"))
        assertEquals(MovieMatcher.key(a), MovieMatcher.key(b))
    }

    @Test fun `fallback tolerates punctuation year and runtime drift`() {
        val a = movie("Spider-Man: Homecoming", 2017, runtimeMinutes = 133)
        val b = movie("Spider Man Homecoming", 2018, runtimeMinutes = 135)
        assertTrue(MovieMatcher.sameFallback(a, b))
    }

    @Test fun `fallback rejects different cuts beyond tolerance`() {
        val a = movie("Example", 2020, runtimeMinutes = 90)
        val b = movie("Example", 2020, runtimeMinutes = 110)
        assertFalse(MovieMatcher.sameFallback(a, b))
    }

    private fun movie(name: String, year: Int, providers: Map<String, String> = emptyMap(), runtimeMinutes: Long = 120) =
        ItemDto("id-$name", name, year = year, providerIds = providers, runtimeTicks = runtimeMinutes * 600_000_000L)
}
