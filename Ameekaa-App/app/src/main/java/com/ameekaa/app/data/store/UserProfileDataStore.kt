package com.ameekaa.app.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ameekaa.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profiles")

class UserProfileDataStore(private val context: Context) {
    
    companion object {
        private val USER_PROFILES_KEY = stringPreferencesKey("user_profiles")
        
        private val initialUserProfiles = listOf(
            UserProfile("1001", "Deepika"),
            UserProfile("1002", "Sami"),
            UserProfile("1003", "Raj"),
            UserProfile("1004", "Cheta")
        )
    }
    
    suspend fun initializeDefaultProfiles() {
        try {
            // Check if data already exists
            val existingProfiles = userProfiles.firstOrNull()
            if (existingProfiles?.isNotEmpty() == true) {
                return // Data already initialized
            }
            
            // Initialize with default data
            context.dataStore.edit { preferences ->
                val json = Json.encodeToString(initialUserProfiles)
                preferences[USER_PROFILES_KEY] = json
            }
        } catch (e: Exception) {
            // Log error but don't crash the app
            android.util.Log.e("UserProfileDataStore", "Error initializing default profiles: ${e.message}", e)
        }
    }
    
    val userProfiles: Flow<List<UserProfile>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[USER_PROFILES_KEY] ?: return@map emptyList<UserProfile>()
            try {
                Json.decodeFromString<List<UserProfile>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
} 


