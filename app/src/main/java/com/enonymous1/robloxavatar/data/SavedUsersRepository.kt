package com.enonymous1.robloxavatar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Usernames the user chose to keep, stored only on the device.
 *
 * The original code kept them as one comma-joined string and tested
 * membership with `String.contains`, so saving "rob" matched "robert"
 * and the list grew a leading comma. A string set has neither problem.
 */
interface SavedUsersRepository {
    val savedUsernames: Flow<List<String>>
    suspend fun save(username: String)
    suspend fun remove(username: String)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "roblox_avatar_viewer",
)

class DataStoreSavedUsersRepository(
    private val context: Context,
) : SavedUsersRepository {

    private val key = stringSetPreferencesKey("saved_usernames")

    override val savedUsernames: Flow<List<String>> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences[key].orEmpty().sorted() }

    override suspend fun save(username: String) {
        val trimmed = username.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[key].orEmpty()
            if (trimmed !in current && current.size < MAX_SAVED_USERS) {
                preferences[key] = current + trimmed
            }
        }
    }

    override suspend fun remove(username: String) {
        context.dataStore.edit { preferences ->
            preferences[key] = preferences[key].orEmpty() - username
        }
    }

    private fun emptyPreferences(): Preferences =
        androidx.datastore.preferences.core.emptyPreferences()

    companion object {
        const val MAX_SAVED_USERS = 50
    }
}
