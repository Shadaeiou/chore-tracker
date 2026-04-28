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
    var meProfile: UserProfile = UserProfile(
        id = "user-1", email = "tester@example.com", displayName = "Tester",
        avatar = null, avatarVersion = 0,
    )
    override suspend fun me(): UserProfile { maybeThrow(); return meProfile }
    override suspend fun patchMe(req: PatchProfileRequest): UserProfile {
        maybeThrow()
        meProfile = meProfile.copy(
            displayName = req.displayName ?: meProfile.displayName,
            avatar = if (req.avatar !== null) req.avatar else meProfile.avatar,
            avatarVersion = if (req.avatar !== null) meProfile.avatarVersion + 1 else meProfile.avatarVersion,
        )
        return meProfile
    }
    val avatarsByUser = mutableMapOf<String, UserAvatarResponse>()
    override suspend fun userAvatar(id: String): UserAvatarResponse {
        maybeThrow()
        return avatarsByUser[id] ?: UserAvatarResponse(avatar = null, avatarVersion = 0)
    }
    var pausedUntil: Long? = null
    override suspend fun household(): HouseholdResponse {
        maybeThrow()
        return HouseholdResponse(Household("hh-1", householdName, 0, pausedUntil), members.toList())
    }
    override suspend fun patchHousehold(req: PatchHouseholdRequest) {
        maybeThrow(); pausedUntil = req.pausedUntil
    }
    var householdName: String = "Home"
    override suspend fun renameHousehold(req: RenameHouseholdRequest) {
        maybeThrow(); householdName = req.name
    }
    override suspend fun createInvite(): Invite { maybeThrow(); return Invite(inviteCode, expiresAt = 0) }
    override suspend fun areas(): List<Area> { maybeThrow(); refreshes += 1; return areas.toList() }
    override suspend fun createArea(req: CreateAreaRequest): Area {
        maybeThrow()
        val a = Area(id = "a-${areas.size + 1}", name = req.name, sortOrder = req.sortOrder, createdAt = 0)
        areas.add(a); createdAreas.add(req); return a
    }
    override suspend fun patchArea(id: String, req: PatchAreaRequest) {
        maybeThrow()
        areas.replaceAll { a -> if (a.id != id) a else a.copy(name = req.name ?: a.name) }
    }
    val copiedAreas = mutableListOf<Pair<String, String>>()
    override suspend fun copyArea(id: String, req: CopyAreaRequest): Area {
        maybeThrow()
        val source = areas.firstOrNull { it.id == id } ?: throw IllegalStateException("not found")
        val newArea = Area(id = "a-${areas.size + 1}", name = req.name, sortOrder = source.sortOrder, createdAt = 0)
        areas.add(newArea)
        // Copy tasks (without completions)
        tasks.filter { it.areaId == id }.forEach { srcTask ->
            tasks.add(srcTask.copy(
                id = "t-${tasks.size + 1}",
                areaId = newArea.id,
                lastDoneAt = null,
                lastDoneBy = null,
            ))
        }
        copiedAreas.add(id to req.name)
        return newArea
    }
    override suspend fun deleteArea(id: String) { maybeThrow(); areas.removeAll { it.id == id } }
    override suspend fun tasks(): List<Task> { maybeThrow(); return tasks.toList() }
    override suspend fun createTask(req: CreateTaskRequest): Task {
        maybeThrow()
        // Resolve name/freq/effort from template if templateId is set and field is missing.
        val tmpl = req.templateId?.let { id -> taskTemplatesData.firstOrNull { it.id == id } }
        val t = Task(
            id = "t-${tasks.size + 1}",
            areaId = req.areaId,
            name = req.name ?: tmpl?.name ?: "unknown",
            frequencyDays = req.frequencyDays ?: tmpl?.suggestedFrequencyDays ?: 7,
            assignedTo = req.assignedTo,
            autoRotate = req.autoRotate,
            effortPoints = req.effortPoints ?: tmpl?.suggestedEffort ?: 1,
            lastDoneAt = req.lastDoneAt,
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
    override suspend fun completeTask(id: String, req: CompleteRequest) {
        maybeThrow()
        completed.add(id)
        val ts = req.at ?: System.currentTimeMillis()
        tasks.replaceAll {
            if (it.id == id) it.copy(lastDoneAt = ts, lastDoneBy = "Tester", snoozedUntil = null) else it
        }
    }
    val snoozed = mutableListOf<Pair<String, Long>>()
    override suspend fun snoozeTask(id: String, req: SnoozeRequest) {
        maybeThrow()
        snoozed.add(id to req.until)
        tasks.replaceAll { if (it.id == id) it.copy(snoozedUntil = req.until) else it }
    }
    val undone = mutableListOf<String>()
    override suspend fun undoLastCompletion(id: String) {
        maybeThrow()
        undone.add(id)
        tasks.replaceAll { if (it.id == id) it.copy(lastDoneAt = null, lastDoneBy = null) else it }
    }
    val deletedCompletions = mutableListOf<String>()
    override suspend fun deleteCompletion(id: String) {
        maybeThrow()
        deletedCompletions.add(id)
        activityFeed.removeAll { it.id == id }
    }
    override suspend fun deleteTask(id: String) { maybeThrow(); tasks.removeAll { it.id == id } }
    override suspend fun activity(before: Long?, limit: Int?): List<ActivityEntry> {
        maybeThrow(); return activityFeed.toList()
    }
    override suspend fun workload(): List<WorkloadEntry> { maybeThrow(); return workloadData.toList() }
    var taskTemplatesData: MutableList<TaskTemplate> = mutableListOf()
    override suspend fun taskTemplates(area: String?): List<TaskTemplate> {
        maybeThrow()
        return if (area == null) taskTemplatesData.toList()
        else taskTemplatesData.filter { it.suggestedArea == area }
    }
    override suspend fun registerDeviceToken(req: DeviceTokenRequest) { maybeThrow() }
    override suspend fun deleteDeviceToken(token: String) { maybeThrow() }
    var todoList: MutableList<TodoItem> = mutableListOf()
    override suspend fun todos(): List<TodoItem> { maybeThrow(); return todoList.toList() }
    override suspend fun createTodo(req: CreateTodoRequest): TodoItem {
        maybeThrow()
        val t = TodoItem(
            id = "todo-${todoList.size + 1}",
            ownerId = nextAuth.userId,
            text = req.text,
            isPublic = req.isPublic,
            createdAt = System.currentTimeMillis(),
        )
        todoList.add(t)
        return t
    }
    override suspend fun markTodoDone(id: String, req: MarkTodoDoneRequest) {
        maybeThrow()
        todoList.replaceAll { if (it.id == id) it.copy(doneAt = req.doneAt) else it }
    }
    override suspend fun editTodo(id: String, req: EditTodoRequest) {
        maybeThrow()
        todoList.replaceAll { if (it.id == id) it.copy(text = req.text, isPublic = req.isPublic) else it }
    }
    override suspend fun deleteTodo(id: String) { maybeThrow(); todoList.removeAll { it.id == id } }
    val removedMembers = mutableListOf<String>()
    override suspend fun removeMember(id: String) {
        maybeThrow()
        removedMembers.add(id)
        members.removeAll { it.id == id }
    }
}
