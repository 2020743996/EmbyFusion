package com.embyfusion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePolicyTest {
    private val second = 10_000_000L

    @Test fun `opening positions do not create resume prompt`() {
        assertEquals(0L, ResumePolicy.normalized(20 * second, 100 * second))
    }

    @Test fun `middle position is retained`() {
        assertEquals(50 * second, ResumePolicy.normalized(50 * second, 100 * second))
    }

    @Test fun `credits threshold clears resume position`() {
        assertEquals(0L, ResumePolicy.normalized(96 * second, 100 * second))
    }
}
