package com.chore.tracker.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DirtinessTest {
    private fun task(lastDoneAt: Long?, frequencyDays: Int): Task =
        Task(
            id = "t",
            areaId = "a",
            name = "x",
            frequencyDays = frequencyDays,
            lastDoneAt = lastDoneAt,
            createdAt = 0,
        )

    @Test fun `never done is fully due`() {
        assertThat(task(lastDoneAt = null, frequencyDays = 7).dirtiness(now = 0L)).isEqualTo(1.0)
    }

    @Test fun `just done is zero`() {
        val now = 1_000_000L
        assertThat(task(lastDoneAt = now, frequencyDays = 7).dirtiness(now)).isWithin(0.0001).of(0.0)
    }

    @Test fun `halfway through window is half`() {
        val now = 1_000_000_000L
        val half = (7L * 86_400_000L) / 2L
        val t = task(lastDoneAt = now - half, frequencyDays = 7)
        assertThat(t.dirtiness(now)).isWithin(0.0001).of(0.5)
    }

    @Test fun `at exactly the window is one`() {
        val now = 1_000_000_000L
        val window = 7L * 86_400_000L
        val t = task(lastDoneAt = now - window, frequencyDays = 7)
        assertThat(t.dirtiness(now)).isWithin(0.0001).of(1.0)
    }

    @Test fun `overdue exceeds one`() {
        val now = 1_000_000_000L
        val t = task(lastDoneAt = now - 14L * 86_400_000L, frequencyDays = 7)
        assertThat(t.dirtiness(now)).isWithin(0.0001).of(2.0)
    }

    @Test fun `zero frequency yields fully due so we never divide by zero`() {
        assertThat(task(lastDoneAt = 100L, frequencyDays = 0).dirtiness(now = 200L)).isEqualTo(1.0)
    }
}
