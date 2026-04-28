package com.chore.tracker.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class SearchTest {
    private val area = Area(id = "a1", name = "Kitchen", createdAt = 0L)
    private val otherArea = Area(id = "a2", name = "Bathroom", createdAt = 0L)

    private fun task(
        name: String = "Wipe counters",
        areaId: String = area.id,
        frequencyDays: Int = 7,
        lastDoneAt: Long? = null,
        assignedToName: String? = null,
        notes: String? = null,
    ): Task = Task(
        id = name,
        areaId = areaId,
        name = name,
        frequencyDays = frequencyDays,
        lastDoneAt = lastDoneAt,
        assignedToName = assignedToName,
        notes = notes,
        createdAt = 0L,
    )

    /** Midnight-aligned reference time so day boundaries are deterministic. */
    private val now: Long = Calendar.getInstance().apply {
        timeInMillis = 1_700_000_000_000L
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val todayStart: Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val dayMs = 86_400_000L

    @Test fun `empty query matches everything`() {
        assertThat(taskMatchesHouseholdSearch(task(), area, "", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(), area, "   ", now)).isTrue()
    }

    @Test fun `substring matches task name`() {
        assertThat(taskMatchesHouseholdSearch(task(name = "Mop floor"), area, "mop", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(name = "Mop floor"), area, "vacuum", now)).isFalse()
    }

    @Test fun `substring matches area name even when task name does not`() {
        assertThat(taskMatchesHouseholdSearch(task(name = "Mop floor"), area, "kitchen", now)).isTrue()
    }

    @Test fun `substring matches assignee name`() {
        val t = task(assignedToName = "Burke")
        assertThat(taskMatchesHouseholdSearch(t, area, "burke", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(t, area, "alice", now)).isFalse()
    }

    @Test fun `substring matches notes content`() {
        val t = task(notes = "Use the strong cleaner")
        assertThat(taskMatchesHouseholdSearch(t, area, "strong", now)).isTrue()
    }

    @Test fun `frequency token matches by days`() {
        assertThat(taskMatchesHouseholdSearch(task(frequencyDays = 7), area, "7d", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(frequencyDays = 14), area, "7d", now)).isFalse()
        assertThat(taskMatchesHouseholdSearch(task(frequencyDays = 1), area, "1d", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(frequencyDays = 30), area, "freq:30", now)).isTrue()
    }

    @Test fun `notes token requires non-blank notes`() {
        assertThat(taskMatchesHouseholdSearch(task(notes = "remember the bleach"), area, "notes", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(notes = null), area, "notes", now)).isFalse()
        assertThat(taskMatchesHouseholdSearch(task(notes = "  "), area, "notes", now)).isFalse()
        assertThat(taskMatchesHouseholdSearch(task(notes = "x"), area, "has:notes", now)).isTrue()
    }

    @Test fun `today token matches a completion done today`() {
        val t = task(lastDoneAt = todayStart + 5 * 60 * 60 * 1000L)
        assertThat(taskMatchesHouseholdSearch(t, area, "today", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(lastDoneAt = todayStart - dayMs), area, "today", now)).isFalse()
    }

    @Test fun `yesterday token matches a completion done yesterday`() {
        val t = task(lastDoneAt = todayStart - dayMs / 2)
        assertThat(taskMatchesHouseholdSearch(t, area, "yesterday", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(lastDoneAt = todayStart), area, "yesterday", now)).isFalse()
    }

    @Test fun `week token matches within last 7 days inclusive`() {
        assertThat(taskMatchesHouseholdSearch(task(lastDoneAt = todayStart - 3 * dayMs), area, "week", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(lastDoneAt = todayStart - 6 * dayMs), area, "week", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(lastDoneAt = todayStart - 8 * dayMs), area, "week", now)).isFalse()
    }

    @Test fun `never token matches tasks with no completion`() {
        assertThat(taskMatchesHouseholdSearch(task(lastDoneAt = null), area, "never", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(task(lastDoneAt = todayStart), area, "never", now)).isFalse()
    }

    @Test fun `multi-token query is AND-ed across fields`() {
        val t = task(name = "Mop floor", frequencyDays = 7, assignedToName = "Burke")
        assertThat(taskMatchesHouseholdSearch(t, area, "burke 7d", now)).isTrue()
        assertThat(taskMatchesHouseholdSearch(t, area, "burke 14d", now)).isFalse()
        assertThat(taskMatchesHouseholdSearch(t, area, "kitchen burke", now)).isTrue()
        // Wrong area name → fails
        val bathroomTask = task(areaId = otherArea.id, assignedToName = "Burke")
        assertThat(taskMatchesHouseholdSearch(bathroomTask, otherArea, "kitchen burke", now)).isFalse()
    }

    @Test fun `special token also passes if it appears as a substring`() {
        // A task literally named "Notes recap" should match query "notes" via substring,
        // even though the structured matcher requires a non-blank notes field.
        val t = task(name = "Notes recap", notes = null)
        assertThat(taskMatchesHouseholdSearch(t, area, "notes", now)).isTrue()
    }

    @Test fun `area-only matcher keeps empty areas visible by name`() {
        assertThat(areaNameMatchesAllTokens(area, "kitchen")).isTrue()
        assertThat(areaNameMatchesAllTokens(area, "kit chen")).isTrue()
        assertThat(areaNameMatchesAllTokens(area, "garage")).isFalse()
    }
}
