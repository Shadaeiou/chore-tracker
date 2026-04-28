package com.chore.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chore.tracker.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A user-customizable status indicator. Empty string = use the default colored
 * dot; non-empty string is rendered as text/emoji in place of the dot. */
data class StatusIndicators(
    val overdue: String = "",
    val dueToday: String = "",
    val notDue: String = "",
)

enum class StatusKey { OVERDUE, DUE_TODAY, NOT_DUE }

interface Session {
    val tokenFlow: Flow<String?>
    val themeModeFlow: Flow<ThemeMode>
    val statusIndicatorsFlow: Flow<StatusIndicators>
    suspend fun token(): String?
    suspend fun setToken(value: String?)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setStatusIndicator(key: StatusKey, value: String)
    suspend fun fcmToken(): String?
    suspend fun setFcmToken(value: String?)
}

private val Context.dataStore by preferencesDataStore(name = "session")

class DataStoreSession(private val context: Context) : Session {
    private val tokenKey = stringPreferencesKey("token")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fcmKey = stringPreferencesKey("fcm_token")
    private val statusOverdueKey = stringPreferencesKey("status_overdue")
    private val statusDueTodayKey = stringPreferencesKey("status_due_today")
    private val statusNotDueKey = stringPreferencesKey("status_not_due")

    override val tokenFlow: Flow<String?> =
        context.dataStore.data.map { it[tokenKey] }

    override val themeModeFlow: Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            when (prefs[themeKey]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    override val statusIndicatorsFlow: Flow<StatusIndicators> =
        context.dataStore.data.map { prefs ->
            StatusIndicators(
                overdue = prefs[statusOverdueKey].orEmpty(),
                dueToday = prefs[statusDueTodayKey].orEmpty(),
                notDue = prefs[statusNotDueKey].orEmpty(),
            )
        }

    override suspend fun token(): String? = tokenFlow.first()

    override suspend fun setToken(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(tokenKey) else prefs[tokenKey] = value
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[themeKey] = mode.name }
    }

    override suspend fun setStatusIndicator(key: StatusKey, value: String) {
        val prefKey = when (key) {
            StatusKey.OVERDUE -> statusOverdueKey
            StatusKey.DUE_TODAY -> statusDueTodayKey
            StatusKey.NOT_DUE -> statusNotDueKey
        }
        context.dataStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(prefKey) else prefs[prefKey] = value
        }
    }

    override suspend fun fcmToken(): String? =
        context.dataStore.data.map { it[fcmKey] }.first()

    override suspend fun setFcmToken(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(fcmKey) else prefs[fcmKey] = value
        }
    }
}

/** In-memory implementation suitable for tests. */
class InMemorySession(
    initial: String? = null,
    initialTheme: ThemeMode = ThemeMode.SYSTEM,
) : Session {
    private val tokenState = MutableStateFlow(initial)
    private val themeState = MutableStateFlow(initialTheme)
    private val indicatorsState = MutableStateFlow(StatusIndicators())
    override val tokenFlow: Flow<String?> = tokenState
    override val themeModeFlow: Flow<ThemeMode> = themeState
    override val statusIndicatorsFlow: Flow<StatusIndicators> = indicatorsState
    override suspend fun token(): String? = tokenState.value
    override suspend fun setToken(value: String?) { tokenState.value = value }
    override suspend fun setThemeMode(mode: ThemeMode) { themeState.value = mode }
    override suspend fun setStatusIndicator(key: StatusKey, value: String) {
        indicatorsState.value = when (key) {
            StatusKey.OVERDUE -> indicatorsState.value.copy(overdue = value)
            StatusKey.DUE_TODAY -> indicatorsState.value.copy(dueToday = value)
            StatusKey.NOT_DUE -> indicatorsState.value.copy(notDue = value)
        }
    }
    private var _fcmToken: String? = null
    override suspend fun fcmToken() = _fcmToken
    override suspend fun setFcmToken(value: String?) { _fcmToken = value }
}
