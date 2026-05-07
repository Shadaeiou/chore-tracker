package com.chore.tracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HouseholdState(
    val household: Household? = null,
    val areas: List<Area> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val members: List<Member> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val workload: List<WorkloadEntry> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val personalRewards: List<Reward> = emptyList(),
    val rewardSettings: RewardSettings = RewardSettings(),
    val effortTotals: List<EffortTotalEntry> = emptyList(),
    val rewardState: HouseholdRewardState = HouseholdRewardState(),
    val personalPoints: PersonalPoints = PersonalPoints(),
    val rpsGames: List<RpsGame> = emptyList(),
    val pausedUntil: Long? = null,
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Pull `sub` (user id) out of a JWT payload without verifying the signature. */
internal fun jwtSub(token: String?): String? {
    if (token.isNullOrBlank()) return null
    val parts = token.split(".")
    if (parts.size < 2) return null
    return try {
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
    } catch (_: Throwable) { null }
}

class Repo(
    val session: Session,
    val api: ChoreApi = ApiFactory.create(session),
    @Suppress("unused") // retained for parity with older test constructors
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(HouseholdState())
    val state: StateFlow<HouseholdState> = _state.asStateFlow()

    suspend fun login(email: String, password: String): AuthResponse {
        val res = api.login(LoginRequest(email, password))
        session.setToken(res.token)
        registerStoredFcmToken()
        return res
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        householdName: String? = null,
        inviteCode: String? = null,
    ): AuthResponse {
        require(householdName != null || inviteCode != null) {
            "must supply householdName or inviteCode"
        }
        val res = api.register(
            RegisterRequest(email, password, displayName, householdName, inviteCode),
        )
        session.setToken(res.token)
        registerStoredFcmToken()
        return res
    }

    /** If an FCM token was stored before login (e.g. from PushService.onNewToken), register it now. */
    private suspend fun registerStoredFcmToken() {
        val token = session.fcmToken() ?: return
        try { api.registerDeviceToken(DeviceTokenRequest(token)) } catch (_: Throwable) {}
    }

    suspend fun logout() {
        session.fcmToken()?.let { token ->
            try { api.deleteDeviceToken(token) } catch (_: Throwable) {}
            session.setFcmToken(null)
        }
        session.setToken(null)
        com.chore.tracker.ui.AvatarCache.clear()
        _state.value = HouseholdState()
    }

    /** Fetch household data in parallel. Marks loading and stores error on failure. */
    suspend fun refresh() {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val areas = api.areas()
            val tasks = api.tasks()
            val household = api.household()
            val activity = api.activity()
            val workload = api.workload()
            val todos = runCatching { api.todos() }.getOrDefault(emptyList())
            val rewards = runCatching { api.rewards("household") }.getOrDefault(emptyList())
            val personalRewards = runCatching { api.rewards("personal") }.getOrDefault(emptyList())
            val rewardSettings = runCatching { api.rewardSettings() }.getOrDefault(RewardSettings())
            val effortTotals = runCatching { api.effortTotals() }.getOrDefault(emptyList())
            val rewardState = runCatching { api.householdRewardState() }.getOrDefault(HouseholdRewardState())
            val personalPoints = runCatching { api.personalPoints() }.getOrDefault(PersonalPoints())
            val rpsGames = runCatching { api.rpsGames() }.getOrDefault(emptyList())
            _state.value = HouseholdState(
                household = household.household,
                areas = areas,
                tasks = tasks,
                members = household.members,
                activity = activity,
                workload = workload,
                todos = todos,
                rewards = rewards,
                personalRewards = personalRewards,
                rewardSettings = rewardSettings,
                effortTotals = effortTotals,
                rewardState = rewardState,
                personalPoints = personalPoints,
                rpsGames = rpsGames,
                pausedUntil = household.household.pausedUntil,
                currentUserId = jwtSub(session.token()),
                isLoading = false,
                error = null,
            )
        } catch (e: CancellationException) {
            _state.value = _state.value.copy(isLoading = false)
            throw e
        } catch (t: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = t.message ?: "load failed")
        }
    }

    /** Light-weight refresh that only re-pulls the data RPS/rewards screens care about. */
    suspend fun refreshRewardsAndRps() {
        try {
            val rewards = runCatching { api.rewards("household") }.getOrDefault(_state.value.rewards)
            val personalRewards = runCatching { api.rewards("personal") }.getOrDefault(_state.value.personalRewards)
            val effortTotals = runCatching { api.effortTotals() }.getOrDefault(_state.value.effortTotals)
            val rewardState = runCatching { api.householdRewardState() }.getOrDefault(_state.value.rewardState)
            val personalPoints = runCatching { api.personalPoints() }.getOrDefault(_state.value.personalPoints)
            val rpsGames = runCatching { api.rpsGames() }.getOrDefault(_state.value.rpsGames)
            _state.value = _state.value.copy(
                rewards = rewards,
                personalRewards = personalRewards,
                effortTotals = effortTotals,
                rewardState = rewardState,
                personalPoints = personalPoints,
                rpsGames = rpsGames,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Best-effort; surface no error.
        }
    }
}
