package com.chore.tracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HouseholdState(
    val areas: List<Area> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val members: List<Member> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val workload: List<WorkloadEntry> = emptyList(),
    val pausedUntil: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class Repo(
    val session: Session,
    val api: ChoreApi = ApiFactory.create(session),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val pollIntervalMs: Long = 60_000L,
) {
    private val _state = MutableStateFlow(HouseholdState())
    val state: StateFlow<HouseholdState> = _state.asStateFlow()

    private var pollJob: Job? = null

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
            _state.value = HouseholdState(
                areas = areas,
                tasks = tasks,
                members = household.members,
                activity = activity,
                workload = workload,
                pausedUntil = household.household.pausedUntil,
                isLoading = false,
                error = null,
            )
        } catch (e: CancellationException) {
            // Coroutine cancellation (e.g. app backgrounded, scope cancelled) is not a real error.
            _state.value = _state.value.copy(isLoading = false)
            throw e
        } catch (t: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = t.message ?: "load failed")
        }
    }

    /** Start a background loop that calls [refresh] every [pollIntervalMs]. Idempotent. */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(pollIntervalMs)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
