package com.ameekaa.app.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ameekaa.app.data.model.StartingPointData
import com.ameekaa.app.data.model.StartingPointData.DepressionSeverityLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "starting_point_data")

class StartingPointDataStore(private val context: Context) {
    companion object {
        private val STARTING_POINT_KEY = stringPreferencesKey("starting_point_data")
        
        // Initial starting point data for all users
        private val initialStartingPointData = listOf(
            StartingPointData(
                userId = "1001",  // Deepika
                noPositiveFeelings = 1,
                lackOfInitiative = 1,
                nothingToLookForward = 0,
                feelingBlue = 0,
                lackOfEnthusiasm = 1,
                lowSelfWorth = 0,
                lifeMeaningless = 0
            ),
            StartingPointData(
                userId = "1002",  // Sami
                noPositiveFeelings = 1,
                lackOfInitiative = 1,
                nothingToLookForward = 0,
                feelingBlue = 0,
                lackOfEnthusiasm = 2,
                lowSelfWorth = 2,
                lifeMeaningless = 0
            ),
            StartingPointData(
                userId = "1003",  // Raj
                noPositiveFeelings = 1,
                lackOfInitiative = 2,
                nothingToLookForward = 1,
                feelingBlue = 1,
                lackOfEnthusiasm = 3,
                lowSelfWorth = 2,
                lifeMeaningless = 2
            ),
            StartingPointData(
                userId = "1004",  // Cheta
                noPositiveFeelings = 2,
                lackOfInitiative = 1,
                nothingToLookForward = 2,
                feelingBlue = 1,
                lackOfEnthusiasm = 1,
                lowSelfWorth = 3,
                lifeMeaningless = 3
            )
        )
    }

    suspend fun initializeDefaultData() {
        try {
            // Check if data already exists
            val existingData = startingPointData.firstOrNull()
            if (existingData?.isNotEmpty() == true) {
                return // Data already initialized
            }
            
            // Initialize with default data
            context.dataStore.edit { preferences ->
                val json = Json.encodeToString(initialStartingPointData)
                preferences[STARTING_POINT_KEY] = json
            }
        } catch (e: Exception) {
            // Log error but don't crash the app
            android.util.Log.e("StartingPointDataStore", "Error initializing default data: ${e.message}", e)
        }
    }
    
    val startingPointData: Flow<List<StartingPointData>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[STARTING_POINT_KEY] ?: return@map emptyList<StartingPointData>()
            try {
                Json.decodeFromString<List<StartingPointData>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
} 


