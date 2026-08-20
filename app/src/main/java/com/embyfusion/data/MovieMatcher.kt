package com.embyfusion.data

import com.embyfusion.data.remote.ItemDto
import java.text.Normalizer
import kotlin.math.abs

object MovieMatcher {
    private val preferredIds = listOf("Tmdb", "Imdb", "Tvdb")

    fun key(item: ItemDto): String {
        preferredIds.firstNotNullOfOrNull { provider ->
            item.providerIds.entries.firstOrNull { it.key.equals(provider, true) }?.value
                ?.takeIf(String::isNotBlank)?.let { "${provider.lowercase()}:$it" }
        }?.let { return it }
        val title = normalize(item.originalTitle ?: item.name)
        return "fallback:$title:${item.year ?: 0}:${runtimeBucket(item.runtimeTicks)}"
    }

    fun sameFallback(a: ItemDto, b: ItemDto): Boolean {
        val titlesMatch = normalize(a.originalTitle ?: a.name) == normalize(b.originalTitle ?: b.name)
        val yearsMatch = a.year == null || b.year == null || abs(a.year - b.year) <= 1
        val runtimeDeltaMinutes = abs(a.runtimeTicks - b.runtimeTicks) / 600_000_000.0
        return titlesMatch && yearsMatch && (a.runtimeTicks == 0L || b.runtimeTicks == 0L || runtimeDeltaMinutes <= 3)
    }

    fun fallbackTitle(item: ItemDto): String = normalize(item.originalTitle ?: item.name)

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase()
        .replace("[^\\p{L}\\p{N}]".toRegex(), "")

    private fun runtimeBucket(ticks: Long): Long = if (ticks == 0L) 0 else (ticks / 600_000_000L) / 3
}
