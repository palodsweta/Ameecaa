package com.ameekaa.app

import android.app.Application
import android.util.Log
import com.ameekaa.app.data.store.UserProfileDataStore
import com.ameekaa.app.data.store.StartingPointDataStore
import com.ameekaa.app.data.store.PersonalityPreferencesDataStore
import com.ameekaa.app.data.store.JoyToolkitDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AmeekaaApplication : Application() {
    private lateinit var userProfileDataStore: UserProfileDataStore
    private lateinit var startingPointDataStore: StartingPointDataStore
    private lateinit var personalityPreferencesDataStore: PersonalityPreferencesDataStore
    private lateinit var joyToolkitDataStore: JoyToolkitDataStore
    
    // Use SupervisorJob to prevent crash if one initialization fails
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            Log.i("AmeekaaApplication", "Setting up data stores...")
            userProfileDataStore = UserProfileDataStore(this)
            startingPointDataStore = StartingPointDataStore(this)
            personalityPreferencesDataStore = PersonalityPreferencesDataStore(this)
            joyToolkitDataStore = JoyToolkitDataStore(this)
            
            Log.i("AmeekaaApplication", "✅ All DataStores created successfully")
            
            // Initialize all data stores
            applicationScope.launch {
                try {
                    Log.i("AmeekaaApplication", "Starting full data initialization...")
                    
                    Log.i("AmeekaaApplication", "🔄 Initializing user profiles...")
                    userProfileDataStore.initializeDefaultProfiles()
                    Log.i("AmeekaaApplication", "✅ User profiles initialized")
                    
                    Log.i("AmeekaaApplication", "🔄 Initializing starting point data...")
                    startingPointDataStore.initializeDefaultData()
                    Log.i("AmeekaaApplication", "✅ Starting point data initialized")
                    
                    Log.i("AmeekaaApplication", "🔄 Initializing personality preferences...")
                    personalityPreferencesDataStore.initializeDefaultData()
                    Log.i("AmeekaaApplication", "✅ Personality preferences initialized")
                    
                    Log.i("AmeekaaApplication", "🔄 Initializing joy toolkit data...")
                    joyToolkitDataStore.initializeDefaultData()
                    Log.i("AmeekaaApplication", "✅ Joy toolkit data initialized")
                    
                    Log.i("AmeekaaApplication", "🎉 All data initialization completed successfully")
                } catch (e: Exception) {
                    Log.e("AmeekaaApplication", "❌ Error during data initialization: ${e.message}", e)
                    // App continues to work even if data initialization fails
                }
            }
        } catch (e: Exception) {
            Log.e("AmeekaaApplication", "❌ Error setting up user profile data store: ${e.message}", e)
            // App continues to work even if DataStore setup fails
        }
    }
} 


