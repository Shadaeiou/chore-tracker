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

interface Session {
    val tokenFlow: Flow<String?>
    val themeModeFlow: Flow<ThemeMode>
    suspend fun token(): String?
    suspend fun setToken(value: String?)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun fcmToken(): String?
    suspend fun setFcmToken(value: String?)
}

private val Context.dataStore by preferencesDataStore(name = "session")

class DataStoreSession(private val context: Context) : Session {
    private val tokenKey = stringPreferencesKey("token")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fcmKey = stringPreferencesKey("fcm_token")

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

    override suspend fun token(): String? = tokenFlow.first()

    override suspend fun setToken(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(tokenKey) else prefs[tokenKey] = value
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[themeKey] = mode.name }
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
    override val tokenFlow: Flow<String?> = tokenState
    override val themeModeFlow: Flow<ThemeMode> = themeState
    override suspend fun token(): String? = tokenState.value
    override suspend fun setToken(value: String?) { tokenState.value = value }
    override suspend fun setThemeMode(mode: ThemeMode) { themeState.value = mode }
    private var _fcmToken: String? = null
    override suspend fun fcmToken() = _fcmToken
    override suspend fun setFcmToken(value: String?) { _fcmToken = value }
}
