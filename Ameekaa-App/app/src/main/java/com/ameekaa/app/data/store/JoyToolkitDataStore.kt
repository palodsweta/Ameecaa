package com.ameekaa.app.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ameekaa.app.data.model.JoyToolkitData
import com.ameekaa.app.data.model.JoyToolkitData.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "joy_toolkit")

class JoyToolkitDataStore(private val context: Context) {
    companion object {
        private val JOY_TOOLKIT_KEY = stringPreferencesKey("joy_toolkit_data")
        
        // Initial joy toolkit data for all users
        private val initialJoyToolkitData = listOf(
            JoyToolkitData(
                userId = "1001",  // Deepika
                sensoryToolkit = SensoryToolkit(
                    sight = "Nature",
                    sound = "Flute"
                ),
                activityToolkit = ActivityToolkit(
                    hobbiesAndPassions = "Drawing",
                    powerOfMovement = "Walking"
                ),
                mindToolkit = MindToolkit(
                    mentalReset = "Deep breathing",
                    selfTalk = "I can do it",
                    curiosity = "Astronomy",
                    focusTools = "Breathing"
                ),
                meaningToolkit = MeaningToolkit(
                    values = "Kindness"
                )
            ),
            JoyToolkitData(
                userId = "1002",  // Sami
                sensoryToolkit = SensoryToolkit(
                    sight = "Art",
                    sound = "Sitar"
                ),
                activityToolkit = ActivityToolkit(
                    hobbiesAndPassions = "Writing",
                    powerOfMovement = "Jogging"
                ),
                mindToolkit = MindToolkit(
                    mentalReset = "Journaling",
                    selfTalk = "God has sent nothing but Angels",
                    curiosity = "Chemistry",
                    focusTools = "Counting"
                ),
                meaningToolkit = MeaningToolkit(
                    values = "Creativity"
                )
            ),
            JoyToolkitData(
                userId = "1003",  // Raj
                sensoryToolkit = SensoryToolkit(
                    sight = "Animals",
                    sound = "Rain"
                ),
                activityToolkit = ActivityToolkit(
                    hobbiesAndPassions = "Guitar",
                    powerOfMovement = "Yoga"
                ),
                mindToolkit = MindToolkit(
                    mentalReset = "7 phase meditation",
                    selfTalk = "God loves me",
                    curiosity = "Hollywood",
                    focusTools = "Visualizing in Mind"
                ),
                meaningToolkit = MeaningToolkit(
                    values = "Growth"
                )
            ),
            JoyToolkitData(
                userId = "1004",  // Cheta
                sensoryToolkit = SensoryToolkit(
                    sight = "Beach",
                    sound = "Beach"
                ),
                activityToolkit = ActivityToolkit(
                    hobbiesAndPassions = "Cooking",
                    powerOfMovement = "Strolling in Garden"
                ),
                mindToolkit = MindToolkit(
                    mentalReset = "Bhastrika",
                    selfTalk = "Life is Beautiful",
                    curiosity = "Music",
                    focusTools = "Witness Meditation"
                ),
                meaningToolkit = MeaningToolkit(
                    values = "Harmony"
                )
            )
        )
    }

    suspend fun initializeDefaultData() {
        try {
            // Check if data already exists
            val existingData = joyToolkitData.firstOrNull()
            if (existingData?.isNotEmpty() == true) {
                return // Data already initialized
            }
            
            // Initialize with default data
            context.dataStore.edit { preferences ->
                val json = Json.encodeToString(initialJoyToolkitData)
                preferences[JOY_TOOLKIT_KEY] = json
            }
        } catch (e: Exception) {
            // Log error but don't crash the app
            android.util.Log.e("JoyToolkitDataStore", "Error initializing default data: ${e.message}", e)
        }
    }
    
    val joyToolkitData: Flow<List<JoyToolkitData>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[JOY_TOOLKIT_KEY] ?: return@map emptyList<JoyToolkitData>()
            try {
                Json.decodeFromString<List<JoyToolkitData>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
} 


