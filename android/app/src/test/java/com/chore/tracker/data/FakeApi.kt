package com.chore.tracker.data

/**
 * Hand-rolled fake of [ChoreApi] for unit/Compose tests. Tracks calls and
 * lets each method's behavior be overridden per-test.
 */
class FakeApi : ChoreApi {
    var areas: MutableList<Area> = mutableListOf()
    var tasks: MutableList<Task> = mutableListOf()
    var members: MutableList<Member> = mutableListOf()
    var activityFeed: MutableList<ActivityEntry> = mutableListOf()
    var workloadData: MutableList<WorkloadEntry> = mutableListOf()
    var inviteCode: String = "INVITE-1234"
    var nextAuth: AuthResponse = AuthResponse("jwt", "user-1", "hh-1")
    var raise: Throwable? = null

    val completed = mutableListOf<String>()
    val createdAreas = mutableListOf<CreateAreaRequest>()
    val createdTasks = mutableListOf<CreateTaskRequest>()
    val patched = mutableListOf<Pair<String, PatchTaskRequest>>()
    var refreshes = 0

    private fun maybeThrow() { raise?.let { throw it } }

    override suspend fun register(req: RegisterRequest): AuthResponse { maybeThrow(); return nextAuth }
    override suspend fun login(req: LoginRequest): AuthResponse { maybeThrow(); return nextAuth }
    override suspend fun household(): HouseholdResponse {
        maybeThrow()
        return HouseholdResponse(Household("hh-1", "Home", 0), members.toList())
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
            assignedTo = req.assignedTo,
            autoRotate = req.autoRotate,
            effortPoints = req.effortPoints,
            createdAt = 0,
        )
        tasks.add(t); createdTasks.add(req); return t
    }
    override suspend fun patchTask(id: String, req: PatchTaskRequest) {
        maybeThrow()
        patched.add(id to req)
        tasks.replaceAll { t ->
            if (t.id != id) t else t.copy(
                name = req.name ?: t.name,
                frequencyDays = req.frequencyDays ?: t.frequencyDays,
                assignedTo = if (req.assignedTo !== null) req.assignedTo else t.assignedTo,
                autoRotate = req.autoRotate ?: t.autoRotate,
                effortPoints = req.effortPoints ?: t.effortPoints,
            )
        }
    }
    override suspend fun completeTask(id: String) {
        maybeThrow()
        completed.add(id)
        tasks.replaceAll { if (it.id == id) it.copy(lastDoneAt = System.currentTimeMillis(), lastDoneBy = "Tester") else it }
    }
    val undone = mutableListOf<String>()
    override suspend fun undoLastCompletion(id: String) {
        maybeThrow()
        undone.add(id)
        tasks.replaceAll { if (it.id == id) it.copy(lastDoneAt = null, lastDoneBy = null) else it }
    }
    override suspend fun deleteTask(id: String) { maybeThrow(); tasks.removeAll { it.id == id } }
    override suspend fun activity(before: Long?, limit: Int?): List<ActivityEntry> {
        maybeThrow(); return activityFeed.toList()
    }
    override suspend fun workload(): List<WorkloadEntry> { maybeThrow(); return workloadData.toList() }
    override suspend fun registerDeviceToken(req: DeviceTokenRequest) { maybeThrow() }
    override suspend fun deleteDeviceToken(token: String) { maybeThrow() }
}
