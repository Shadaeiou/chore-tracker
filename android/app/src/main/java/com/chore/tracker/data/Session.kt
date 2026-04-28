package com.chore.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chore.tracker.ui.ThemeMode
import com.chore.tracker.ui.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Per-status user override. Precedence at render time:
 *   1. text (emoji/short string) — if non-empty, rendered in place of the dot
 *   2. color (hex "#RRGGBB") — if non-empty, used to tint the default dot
 *   3. default red/yellow/green dot otherwise */
data class StatusIndicators(
    val overdue: String = "",
    val dueToday: String = "",
    val notDue: String = "",
    val overdueColor: String = "",
    val dueTodayColor: String = "",
    val notDueColor: String = "",
)

enum class StatusKey { OVERDUE, DUE_TODAY, NOT_DUE }

interface Session {
    val tokenFlow: Flow<String?>
    val themeModeFlow: Flow<ThemeMode>
    val themePaletteFlow: Flow<ThemePalette>
    val statusIndicatorsFlow: Flow<StatusIndicators>
    suspend fun token(): String?
    suspend fun setToken(value: String?)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemePalette(palette: ThemePalette)
    suspend fun setStatusIndicator(key: StatusKey, value: String)
    suspend fun setStatusColor(key: StatusKey, hex: String)
    suspend fun fcmToken(): String?
    suspend fun setFcmToken(value: String?)
}

private val Context.dataStore by preferencesDataStore(name = "session")

class DataStoreSession(private val context: Context) : Session {
    private val tokenKey = stringPreferencesKey("token")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val paletteKey = stringPreferencesKey("theme_palette")
    private val fcmKey = stringPreferencesKey("fcm_token")
    private val statusOverdueKey = stringPreferencesKey("status_overdue")
    private val statusDueTodayKey = stringPreferencesKey("status_due_today")
    private val statusNotDueKey = stringPreferencesKey("status_not_due")
    private val statusOverdueColorKey = stringPreferencesKey("status_overdue_color")
    private val statusDueTodayColorKey = stringPreferencesKey("status_due_today_color")
    private val statusNotDueColorKey = stringPreferencesKey("status_not_due_color")

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

    override val themePaletteFlow: Flow<ThemePalette> =
        context.dataStore.data.map { prefs ->
            runCatching { ThemePalette.valueOf(prefs[paletteKey] ?: "GREEN") }
                .getOrDefault(ThemePalette.GREEN)
        }

    override val statusIndicatorsFlow: Flow<StatusIndicators> =
        context.dataStore.data.map { prefs ->
            StatusIndicators(
                overdue = prefs[statusOverdueKey].orEmpty(),
                dueToday = prefs[statusDueTodayKey].orEmpty(),
                notDue = prefs[statusNotDueKey].orEmpty(),
                overdueColor = prefs[statusOverdueColorKey].orEmpty(),
                dueTodayColor = prefs[statusDueTodayColorKey].orEmpty(),
                notDueColor = prefs[statusNotDueColorKey].orEmpty(),
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

    override suspend fun setThemePalette(palette: ThemePalette) {
        context.dataStore.edit { prefs -> prefs[paletteKey] = palette.name }
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

    override suspend fun setStatusColor(key: StatusKey, hex: String) {
        val prefKey = when (key) {
            StatusKey.OVERDUE -> statusOverdueColorKey
            StatusKey.DUE_TODAY -> statusDueTodayColorKey
            StatusKey.NOT_DUE -> statusNotDueColorKey
        }
        context.dataStore.edit { prefs ->
            if (hex.isBlank()) prefs.remove(prefKey) else prefs[prefKey] = hex
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
    private val paletteState = MutableStateFlow(ThemePalette.GREEN)
    private val indicatorsState = MutableStateFlow(StatusIndicators())
    override val tokenFlow: Flow<String?> = tokenState
    override val themeModeFlow: Flow<ThemeMode> = themeState
    override val themePaletteFlow: Flow<ThemePalette> = paletteState
    override val statusIndicatorsFlow: Flow<StatusIndicators> = indicatorsState
    override suspend fun token(): String? = tokenState.value
    override suspend fun setToken(value: String?) { tokenState.value = value }
    override suspend fun setThemeMode(mode: ThemeMode) { themeState.value = mode }
    override suspend fun setThemePalette(palette: ThemePalette) { paletteState.value = palette }
    override suspend fun setStatusIndicator(key: StatusKey, value: String) {
        indicatorsState.value = when (key) {
            StatusKey.OVERDUE -> indicatorsState.value.copy(overdue = value)
            StatusKey.DUE_TODAY -> indicatorsState.value.copy(dueToday = value)
            StatusKey.NOT_DUE -> indicatorsState.value.copy(notDue = value)
        }
    }
    override suspend fun setStatusColor(key: StatusKey, hex: String) {
        indicatorsState.value = when (key) {
            StatusKey.OVERDUE -> indicatorsState.value.copy(overdueColor = hex)
            StatusKey.DUE_TODAY -> indicatorsState.value.copy(dueTodayColor = hex)
            StatusKey.NOT_DUE -> indicatorsState.value.copy(notDueColor = hex)
        }
    }
    private var _fcmToken: String? = null
    override suspend fun fcmToken() = _fcmToken
    override suspend fun setFcmToken(value: String?) { _fcmToken = value }
}
