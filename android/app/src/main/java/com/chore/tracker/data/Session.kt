package com.chore.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface Session {
    val tokenFlow: Flow<String?>
    suspend fun token(): String?
    suspend fun setToken(value: String?)
}

private val Context.dataStore by preferencesDataStore(name = "session")

class DataStoreSession(private val context: Context) : Session {
    private val tokenKey = stringPreferencesKey("token")

    override val tokenFlow: Flow<String?> =
        context.dataStore.data.map { it[tokenKey] }

    override suspend fun token(): String? = tokenFlow.first()

    override suspend fun setToken(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(tokenKey) else prefs[tokenKey] = value
        }
    }
}

/** In-memory implementation suitable for tests. */
class InMemorySession(initial: String? = null) : Session {
    private val flow = MutableStateFlow(initial)
    override val tokenFlow: Flow<String?> = flow
    override suspend fun token(): String? = flow.value
    override suspend fun setToken(value: String?) { flow.value = value }
}
