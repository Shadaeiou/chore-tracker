package com.chore.tracker.data

/**
 * Hand-rolled fake of [ChoreApi] for unit/Compose tests. Tracks calls and
 * lets each method's behavior be overridden per-test.
 */
class FakeApi : ChoreApi {
    var areas: MutableList<Area> = mutableListOf()
    var tasks: MutableList<Task> = mutableListOf()
    var inviteCode: String = "INVITE-1234"
    var nextAuth: AuthResponse = AuthResponse("jwt", "user-1", "hh-1")
    var raise: Throwable? = null

    val completed = mutableListOf<String>()
    val createdAreas = mutableListOf<CreateAreaRequest>()
    val createdTasks = mutableListOf<CreateTaskRequest>()
    var refreshes = 0

    private fun maybeThrow() { raise?.let { throw it } }

    override suspend fun register(req: RegisterRequest): AuthResponse { maybeThrow(); return nextAuth }
    override suspend fun login(req: LoginRequest): AuthResponse { maybeThrow(); return nextAuth }
    override suspend fun household(): HouseholdResponse {
        maybeThrow()
        return HouseholdResponse(Household("hh-1", "Home", 0), emptyList())
    }
    override suspend fun createInvite(): Invite { maybeThrow(); return Invite(inviteCode, expiresAt = 0) }
    override suspend fun areas(): List<Area> { maybeThrow(); refreshes += 1; return areas.toList() }
    override suspend fun createArea(req: CreateAreaRequest): Area {
        maybeThrow()
        val a = Area(id = "a-${areas.size + 1}", name = req.name, sortOrder = req.sortOrder, createdAt = 0)
        areas.add(a); createdAreas.add(req); return a
    }
    override suspend fun deleteArea(id: String) { maybeThrow(); areas.removeAll { it.id == id } }
    override suspend fun tasks(): List<Task> { maybeThrow(); return tasks.toList() }
    override suspend fun createTask(req: CreateTaskRequest): Task {
        maybeThrow()
        val t = Task(
            id = "t-${tasks.size + 1}",
            areaId = req.areaId,
            name = req.name,
            frequencyDays = req.frequencyDays,
            createdAt = 0,
        )
        tasks.add(t); createdTasks.add(req); return t
    }
    override suspend fun completeTask(id: String) {
        maybeThrow()
        completed.add(id)
        tasks.replaceAll { if (it.id == id) it.copy(lastDoneAt = System.currentTimeMillis(), lastDoneBy = "Tester") else it }
    }
    override suspend fun deleteTask(id: String) { maybeThrow(); tasks.removeAll { it.id == id } }
}
