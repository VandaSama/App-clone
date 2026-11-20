package com.multifb.profiles.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.multiProfileDataStore by preferencesDataStore(name = "multi_profile_fb")

/** Menyimpan pemetaan userId BlackBox dan nama profil di Preferences DataStore. */
class ProfileStore(context: Context) {
    private val dataStore = context.applicationContext.multiProfileDataStore

    val profiles: Flow<List<FbProfile>> = dataStore.data.map { prefs ->
        prefs[PROFILE_IDS].orEmpty()
            .mapNotNull { rawId ->
                val id = rawId.toIntOrNull() ?: return@mapNotNull null
                FbProfile(
                    userId = id,
                    name = prefs[nameKey(id)] ?: "Profil $id"
                )
            }
            .sortedBy { it.userId }
    }

    suspend fun add(profile: FbProfile) {
        dataStore.edit { prefs ->
            prefs[PROFILE_IDS] = prefs[PROFILE_IDS].orEmpty() + profile.userId.toString()
            prefs[nameKey(profile.userId)] = profile.name
        }
    }

    suspend fun rename(userId: Int, newName: String) {
        dataStore.edit { prefs ->
            if (prefs[PROFILE_IDS].orEmpty().contains(userId.toString())) {
                prefs[nameKey(userId)] = newName
            }
        }
    }

    suspend fun remove(userId: Int) {
        dataStore.edit { prefs ->
            prefs[PROFILE_IDS] = prefs[PROFILE_IDS].orEmpty() - userId.toString()
            prefs.remove(nameKey(userId))
        }
    }

    companion object {
        private val PROFILE_IDS = stringSetPreferencesKey("profile_user_ids")
        private fun nameKey(userId: Int) = stringPreferencesKey("profile_name_$userId")
    }
}
