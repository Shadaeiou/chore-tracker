package com.chore.tracker.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import com.chore.tracker.data.ActivityEntry
import com.chore.tracker.data.Member
import com.chore.tracker.data.WorkloadEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RepoTest {
    private fun newRepo(scope: CoroutineScope, fake: FakeApi = FakeApi()) =
        Repo(
            session = InMemorySession(),
            api = fake,
            scope = scope,
            pollIntervalMs = 1_000,
        )

    @Test fun `login persists token via session`() = runTest {
        val session = InMemorySession()
        val repo = Repo(session, api = FakeApi(), scope = this)
        repo.login("e@x.com", "longenoughpass")
        assertThat(session.token()).isEqualTo("jwt")
    }

    @Test fun `register requires householdName or inviteCode`() = runTest {
        val repo = Repo(InMemorySession(), api = FakeApi(), scope = this)
        try {
            repo.register("e@x.com", "longenoughpass", "name")
            error("expected to throw")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("householdName")
        }
    }

    @Test fun `refresh populates state with areas and tasks`() = runTest {
        val fake = FakeApi().apply {
            areas.add(Area("a1", "Kitchen", null, 0, 0))
            tasks.add(Task("t1", "a1", "Mop", 7, null, null, 0))
        }
        val repo = newRepo(this, fake)
        repo.refresh()
        val state = repo.state.value
        assertThat(state.areas).hasSize(1)
        assertThat(state.tasks.first().name).isEqualTo("Mop")
        assertThat(state.error).isNull()
        assertThat(state.isLoading).isFalse()
    }

    @Test fun `refresh records error message on failure`() = runTest {
        val fake = FakeApi().apply { raise = IOException("network down") }
        val repo = newRepo(this, fake)
        repo.refresh()
        assertThat(repo.state.value.error).contains("network down")
    }

    @Test fun `polling refreshes every interval until stopped`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val fake = FakeApi()
        val repo = Repo(InMemorySession(), api = fake, scope = scope, pollIntervalMs = 1_000)

        repo.startPolling()
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        repo.stopPolling()
        val refreshesAfterStop = fake.refreshes
        advanceTimeBy(5_000)
        runCurrent()

        // Polled at t=0, ~1000, ~2000.
        assertThat(refreshesAfterStop).isAtLeast(3)
        assertThat(fake.refreshes).isEqualTo(refreshesAfterStop)
    }

    @Test fun `refresh populates members, activity, and workload`() = runTest {
        val fake = FakeApi().apply {
            members.add(Member("u1", "Alice", "alice@example.com"))
            activityFeed.add(ActivityEntry("c1", "t1", "Vacuum", "Living room", "Alice", 0L))
            workloadData.add(WorkloadEntry("u1", "Alice", 3))
        }
        val repo = newRepo(this, fake)
        repo.refresh()
        val state = repo.state.value
        assertThat(state.members).hasSize(1)
        assertThat(state.activity.first().taskName).isEqualTo("Vacuum")
        assertThat(state.workload.first().effortPoints).isEqualTo(3)
    }

    @Test fun `logout clears session token and resets state`() = runTest {
        val session = InMemorySession(initial = "stale-token")
        val fake = FakeApi().apply { areas.add(Area("a", "A", null, 0, 0)) }
        val repo = Repo(session, api = fake, scope = this)
        repo.refresh()
        assertThat(repo.state.value.areas).hasSize(1)
        repo.logout()
        assertThat(session.token()).isNull()
        assertThat(repo.state.value.areas).isEmpty()
    }
}
