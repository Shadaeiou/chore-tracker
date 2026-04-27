package com.chore.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

class Session(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")

    val tokenFlow: Flow<String?> =
        context.dataStore.data.map { it[tokenKey] }

    suspend fun token(): String? = tokenFlow.first()

    suspend fun setToken(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(tokenKey) else prefs[tokenKey] = value
        }
    }
}
