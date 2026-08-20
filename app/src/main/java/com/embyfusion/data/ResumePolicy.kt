package com.embyfusion.data

object ResumePolicy {
    private const val MIN_RESUME_TICKS = 30L * 10_000_000L
    private const val COMPLETION_PERCENT = 0.95

    /** Avoids annoying resume prompts near the opening and after the credits threshold. */
    fun normalized(positionTicks: Long, runtimeTicks: Long): Long {
        if (positionTicks < MIN_RESUME_TICKS || runtimeTicks <= 0L) return 0L
        if (positionTicks >= (runtimeTicks * COMPLETION_PERCENT).toLong()) return 0L
        return positionTicks.coerceAtMost(runtimeTicks)
    }
}
