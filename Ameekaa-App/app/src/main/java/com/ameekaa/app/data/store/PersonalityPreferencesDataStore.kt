package com.ameekaa.app.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ameekaa.app.data.model.PersonalityPreferencesData
import com.ameekaa.app.data.model.PersonalityPreferencesData.*
import com.ameekaa.app.data.model.PersonalityTraitScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "personality_preferences")

class PersonalityPreferencesDataStore(private val context: Context) {
    companion object {
        private val PREFERENCES_KEY = stringPreferencesKey("personality_preferences")
        
        // Initial personality preferences data for all users
        private val initialPreferencesData = listOf(
            PersonalityPreferencesData(
                userId = "1001",  // Deepika
                weekendChoice = WeekendChoice.SOLO_EXPLORATION,        // A
                vacationChoice = VacationChoice.DETAILED_PLANNING,     // A
                meTimeChoice = MeTimeChoice.CREATIVE_PROJECT,          // A
                problemSolvingChoice = ProblemSolvingChoice.PRACTICAL_SOLUTIONS,  // B
                changeResponseChoice = ChangeResponseChoice.ANXIOUS     // B
            ),
            PersonalityPreferencesData(
                userId = "1002",  // Sami
                weekendChoice = WeekendChoice.GROUP_ACTIVITY,          // B
                vacationChoice = VacationChoice.SPONTANEOUS,           // B
                meTimeChoice = MeTimeChoice.CREATIVE_PROJECT,          // A
                problemSolvingChoice = ProblemSolvingChoice.EMOTIONAL_SUPPORT,    // A
                changeResponseChoice = ChangeResponseChoice.ANXIOUS     // B
            ),
            PersonalityPreferencesData(
                userId = "1003",  // Raj
                weekendChoice = WeekendChoice.GROUP_ACTIVITY,          // B
                vacationChoice = VacationChoice.SPONTANEOUS,           // B
                meTimeChoice = MeTimeChoice.CREATIVE_PROJECT,          // A
                problemSolvingChoice = ProblemSolvingChoice.EMOTIONAL_SUPPORT,    // A
                changeResponseChoice = ChangeResponseChoice.ADAPTABLE   // A
            ),
            PersonalityPreferencesData(
                userId = "1004",  // Cheta
                weekendChoice = WeekendChoice.SOLO_EXPLORATION,        // A
                vacationChoice = VacationChoice.SPONTANEOUS,           // B
                meTimeChoice = MeTimeChoice.CREATIVE_PROJECT,          // A
                problemSolvingChoice = ProblemSolvingChoice.PRACTICAL_SOLUTIONS,  // B
                changeResponseChoice = ChangeResponseChoice.ADAPTABLE   // A
            )
        )
    }

    suspend fun initializeDefaultData() {
        try {
            // Check if data already exists
            val existingData = personalityPreferencesData.firstOrNull()
            if (existingData?.isNotEmpty() == true) {
                return // Data already initialized
            }
            
            // Initialize with default data
            context.dataStore.edit { preferences ->
                val json = Json.encodeToString(initialPreferencesData)
                preferences[PREFERENCES_KEY] = json
            }
        } catch (e: Exception) {
            // Log error but don't crash the app
            android.util.Log.e("PersonalityPreferencesDataStore", "Error initializing default data: ${e.message}", e)
        }
    }
    
    val personalityPreferencesData: Flow<List<PersonalityPreferencesData>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[PREFERENCES_KEY] ?: return@map emptyList<PersonalityPreferencesData>()
            try {
                Json.decodeFromString<List<PersonalityPreferencesData>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
} 


