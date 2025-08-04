package com.ameekaa.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.ameekaa.app.data.store.UserProfileDataStore
import com.ameekaa.app.data.store.StartingPointDataStore
import com.ameekaa.app.data.store.PersonalityPreferencesDataStore
import com.ameekaa.app.data.store.JoyToolkitDataStore
import com.ameekaa.app.data.model.UserProfile
import com.ameekaa.app.data.model.StartingPointData
import com.ameekaa.app.data.model.PersonalityPreferencesData
import com.ameekaa.app.data.model.PersonalityPreferencesData.PersonalityTrait
import com.ameekaa.app.data.model.JoyToolkitData
import com.ameekaa.app.sentiment.AudioSentimentAnalyzer
import com.ameekaa.app.data.store.SentimentAnalysisDataStore
import com.ameekaa.app.data.model.SentimentAnalysisResult
import com.ameekaa.app.data.model.SentimentAnalysisStats
import com.ameekaa.app.data.model.EmotionalIntensity
import com.ameekaa.app.data.model.Valence
import com.ameekaa.app.data.model.Arousal
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable

class MainActivity : AppCompatActivity() {
    
    private lateinit var userSpinner: Spinner
    private lateinit var startingPointCard: LinearLayout
    private lateinit var personalityCard: LinearLayout
    private lateinit var severityLevel: TextView
    private lateinit var depressionScore: TextView
    private lateinit var personalityTraits: TextView
    
    // Audio processing UI elements (following DiarizationTest pattern)
    private lateinit var audioProcessingSection: LinearLayout
    private lateinit var btnSpeakerEnrollment: Button
    private lateinit var btnMeetingAudio: Button
    private lateinit var btnCustomAudio: Button
    private lateinit var enrollmentFileStatus: TextView
    private lateinit var meetingFileStatus: TextView
    private lateinit var selectedAudioInfo: TextView
    private lateinit var diarizationTimeDisplay: TextView
    private lateinit var btnPlayOutput: Button
    private lateinit var btnAnalyzeSentiment: Button
    private lateinit var btnNudgeGeneration: Button
    private lateinit var sentimentAnalysisStatus: TextView
    private lateinit var nudgeGenerationStatus: TextView
    private lateinit var sentimentProcessingTimeDisplay: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    
    private var userProfiles: List<UserProfile> = emptyList()
    private var startingPointData: List<StartingPointData> = emptyList()
    private var personalityData: List<PersonalityPreferencesData> = emptyList()
    private var joyToolkitData: List<JoyToolkitData> = emptyList()
    
    // Audio processing variables (following DiarizationTest pattern)
    private var enrollmentUri: Uri? = null
    private var meetingUri: Uri? = null
    private var outputFileUri: Uri? = null
    private var selectedUserName: String = ""
    private var selectedUserId: String = ""
    private lateinit var audioSentimentAnalyzer: AudioSentimentAnalyzer
    
    // File picker launchers (following DiarizationTest pattern)
    private val enrollmentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                enrollmentUri = uri
                val fileName = getFileName(uri)
                enrollmentFileStatus.text = fileName
                enrollmentFileStatus.setTextColor(getColor(android.R.color.holo_green_light))
                updateProcessButtonState()
            }
        }
    }
    
    private val meetingPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                meetingUri = uri
                val fileName = getFileName(uri)
                meetingFileStatus.text = fileName
                meetingFileStatus.setTextColor(getColor(android.R.color.holo_green_light))
                updateProcessButtonState()
            }
        }
    }

    // Initialize diarization engine (same as DiarizationTest)
    private fun createDiarizationEngine(modelPath: String): DynamicQuantizedDiarizationEngine {
        val audioProcessor = DynamicQuantizedAudioProcessor(
            context = this,
            modelPath = modelPath,
            sampleRate = 16000
        )
        return DynamicQuantizedDiarizationEngine(
            modelPath = modelPath,
            config = mapOf(
                "segment_length" to 2.0,
                "segment_step" to 2.0,
                "min_segment_ratio" to 0.5
            ),
            audioProcessor = audioProcessor
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        initializeAudioProcessing()
        initializeDataStoresLightweight()
    }
    
    private fun setupUI() {
        // Set up toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Find UI elements
        userSpinner = findViewById(R.id.user_spinner)
        startingPointCard = findViewById(R.id.starting_point_card)
        personalityCard = findViewById(R.id.personality_card)
        severityLevel = findViewById(R.id.severity_level)
        depressionScore = findViewById(R.id.depression_score)
        personalityTraits = findViewById(R.id.personality_traits)
        
        // Find audio processing UI elements
        audioProcessingSection = findViewById(R.id.audio_processing_section)
        btnSpeakerEnrollment = findViewById(R.id.btn_speaker_enrollment)
        btnMeetingAudio = findViewById(R.id.btn_meeting_audio)
        btnCustomAudio = findViewById(R.id.btn_custom_audio)
        enrollmentFileStatus = findViewById(R.id.enrollment_file_status)
        meetingFileStatus = findViewById(R.id.meeting_file_status)
        selectedAudioInfo = findViewById(R.id.selected_audio_info)
        diarizationTimeDisplay = findViewById(R.id.diarization_time_display)
        btnAnalyzeSentiment = findViewById(R.id.btn_analyze_sentiment)
        btnNudgeGeneration = findViewById(R.id.btn_nudge_generation)
        btnPlayOutput = findViewById(R.id.btn_play_output)
        sentimentAnalysisStatus = findViewById(R.id.sentiment_analysis_status)
        nudgeGenerationStatus = findViewById(R.id.nudge_generation_status)
        sentimentProcessingTimeDisplay = findViewById(R.id.sentiment_processing_time_display)
        statusText = findViewById(R.id.status_text)
        progressBar = findViewById(R.id.progress_bar)
        
        setupAudioButtonListeners()
        
        // Set up spinner listener
        userSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) { // Skip "Select User" item
                    val selectedUser = userProfiles[position - 1]
                    selectedUserName = selectedUser.userName
                    selectedUserId = selectedUser.userId
                    
                    // 1. First display user profile data
                    displayUserData(selectedUser.userId)
                    
                    // 2. Then show audio processing section
                    showAudioProcessingSection()
                } else {
                    hideUserData()
                    hideAudioProcessingSection()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                hideUserData()
                hideAudioProcessingSection()
            }
        }
    }
    
    private fun initializeAudioProcessing() {
        lifecycleScope.launch {
            try {
                Log.i("MainActivity", "🎤 Initializing AudioSentimentAnalyzer...")
                audioSentimentAnalyzer = AudioSentimentAnalyzer(this@MainActivity)
                
                // Initialize the model
                val initResult = audioSentimentAnalyzer.initializeModel()
                if (!initResult) {
                    Log.e("MainActivity", "❌ Failed to initialize Gemma model")
                    return@launch
                }
                
                Log.i("MainActivity", "✅ AudioSentimentAnalyzer initialized successfully")
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error initializing AudioSentimentAnalyzer", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun initializeDataStoresLightweight() {
        // Initialize data stores in background without blocking UI
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i("MainActivity", "🔄 Initializing data stores...")
                
                val userProfileStore = UserProfileDataStore(this@MainActivity)
                val startingPointStore = StartingPointDataStore(this@MainActivity)
                val personalityStore = PersonalityPreferencesDataStore(this@MainActivity)
                val joyToolkitStore = JoyToolkitDataStore(this@MainActivity)
                val sentimentStore = SentimentAnalysisDataStore(this@MainActivity)
                
                // Initialize with default data
                userProfileStore.initializeDefaultProfiles()
                startingPointStore.initializeDefaultData()
                personalityStore.initializeDefaultData()
                joyToolkitStore.initializeDefaultData()
                sentimentStore.initializeDefaultSentimentData()
                
                Log.i("MainActivity", "✅ Data stores initialized successfully")
                
                // Now load the data after initialization is complete
                loadDataAfterInitialization(userProfileStore, startingPointStore, personalityStore, joyToolkitStore)
                
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Error initializing data stores: ${e.message}", e)
            }
        }
    }
    
    private suspend fun loadDataAfterInitialization(
        userProfileStore: UserProfileDataStore,
        startingPointStore: StartingPointDataStore,
        personalityStore: PersonalityPreferencesDataStore,
        joyToolkitStore: JoyToolkitDataStore
    ) {
        try {
            Log.i("MainActivity", "🔄 Loading data after initialization...")
            
            // Load user profiles using Flow
            userProfiles = userProfileStore.userProfiles.first()
            Log.i("MainActivity", "Loaded ${userProfiles.size} user profiles")
            
            // Load starting point data using Flow
            startingPointData = startingPointStore.startingPointData.first()
            Log.i("MainActivity", "Loaded ${startingPointData.size} starting point records")
            
            // Load personality data (if available)
            try {
                personalityData = personalityStore.personalityPreferencesData.first()
                Log.i("MainActivity", "Loaded ${personalityData.size} personality records")
            } catch (e: Exception) {
                Log.w("MainActivity", "Personality data not available: ${e.message}")
            }
            
            // Load joy toolkit data (if available)
            try {
                joyToolkitData = joyToolkitStore.joyToolkitData.first()
                Log.i("MainActivity", "Loaded ${joyToolkitData.size} joy toolkit records")
            } catch (e: Exception) {
                Log.w("MainActivity", "Joy toolkit data not available: ${e.message}")
            }
            
            // Update UI on main thread
            withContext(Dispatchers.Main) {
                setupUserSpinner()
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading data: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Error loading data", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupAudioButtonListeners() {
        btnSpeakerEnrollment.setOnClickListener {
            openAudioPicker(enrollmentPicker, "Select Speaker Enrollment Audio")
        }
        
        btnMeetingAudio.setOnClickListener {
            openAudioPicker(meetingPicker, "Select Meeting Audio")
        }
        
        btnCustomAudio.setOnClickListener {
            // Diarization Process button
            processCustomAudioFiles()
        }
        
        btnAnalyzeSentiment.setOnClickListener {
            // Display sentiment analysis data from the data store
            performSentimentAnalysisOnProcessedAudio()
        }
        
        btnNudgeGeneration.setOnClickListener {
            // Generate personalized nudge based on user profile and sentiment data
            performNudgeGeneration()
        }
        
        btnPlayOutput.setOnClickListener {
            playOutputAudio()
        }
    }
    
    // Same audio picker method as DiarizationTest
    private fun openAudioPicker(picker: androidx.activity.result.ActivityResultLauncher<Intent>, title: String) {
        val intents = mutableListOf<Intent>()
        
        val primaryIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/*",
                "audio/wav",
                "audio/x-wav", 
                "audio/mpeg",
                "audio/mp3",
                "audio/ogg",
                "audio/flac",
                "audio/aac",
                "audio/m4a"
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            putExtra("android.content.extra.FANCY", true)
            putExtra("android.content.extra.LOCAL_ONLY", true)
        }
        intents.add(primaryIntent)
        
        val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/*",
                "audio/wav",
                "audio/x-wav", 
                "audio/mpeg",
                "audio/mp3",
                "audio/ogg",
                "audio/flac",
                "audio/aac",
                "audio/m4a"
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooserIntent = Intent.createChooser(primaryIntent, title)
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(fallbackIntent))
        
        try {
            Log.i("MainActivity", "Opening file picker: $title")
            picker.launch(chooserIntent)
        } catch (e: Exception) {
            Log.w("MainActivity", "Chooser failed, trying fallback: ${e.message}")
            try {
                picker.launch(primaryIntent)
            } catch (e2: Exception) {
                Log.w("MainActivity", "Primary intent failed, trying simple GET_CONTENT: ${e2.message}")
                picker.launch(fallbackIntent)
            }
        }
    }
    
    private fun updateProcessButtonState() {
        val bothFilesSelected = enrollmentUri != null && meetingUri != null && selectedUserId.isNotEmpty()
        btnCustomAudio.isEnabled = bothFilesSelected
        
        if (bothFilesSelected) {
            btnCustomAudio.text = "Diarization Process"
            btnCustomAudio.backgroundTintList = getColorStateList(android.R.color.holo_blue_bright)
        } else {
            btnCustomAudio.text = "Diarization Process (Select Files First)"
            btnCustomAudio.backgroundTintList = getColorStateList(android.R.color.darker_gray)
        }
    }
    
    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                it.getString(nameIndex) ?: "Unknown"
            } else "Unknown"
        } ?: "Unknown"
    }
    
    // Same processing as DiarizationTest but followed by sentiment analysis
    private fun processCustomAudioFiles() {
        if (enrollmentUri == null || meetingUri == null) {
            Toast.makeText(this, "Please select both enrollment and meeting audio files", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress and hide previous results
        progressBar.visibility = View.VISIBLE
        btnCustomAudio.isEnabled = false
        btnPlayOutput.visibility = View.GONE
        btnAnalyzeSentiment.visibility = View.GONE
        btnNudgeGeneration.visibility = View.GONE
        diarizationTimeDisplay.visibility = View.GONE
        outputFileUri = null
        
        lifecycleScope.launch {
            updateStatus("Processing audio files...")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Start tracking total diarization time
                val diarizationStartTime = System.currentTimeMillis()
                
                val enrollmentPath = copyUriToInternalStorage(enrollmentUri!!, "enrollment.wav")
                val meetingPath = copyUriToInternalStorage(meetingUri!!, "meeting.wav")
                val modelPath = copyModelToInternalStorage()

                updateStatus("Custom files ready")
                updateStatus("Starting speaker diarization...")

                val diarizationEngine = createDiarizationEngine(modelPath)
                
                updateStatus("Step 1/3: Processing enrollment audio...")

                val enrollmentEmbedding = enrollSpeaker(
                    enrollmentPath = enrollmentPath,
                    speakerName = "user_${selectedUserId}",
                    modelPath = modelPath,
                    sampleRate = 16000,
                    context = this@MainActivity
                )
                
                updateStatus("Step 2/3: Processing meeting audio...")

                val segments = diarizationEngine.diarizeMeeting(
                    meetingPath = meetingPath,
                    enrollmentEmbedding = enrollmentEmbedding,
                    speakerName = "user_${selectedUserId}",
                    threshold = 0.6f 
                )
                
                updateStatus("Step 3/3: Extracting speaker segments...")

                if (segments.isNotEmpty()) {
                    val outputPath = filesDir.absolutePath + "/user_${selectedUserId}_segments.wav"
                    diarizationEngine.extractSegments(
                        meetingPath = meetingPath,
                        segments = segments,
                        outputPath = outputPath
                    )
                    
                    // Calculate total diarization time
                    val diarizationEndTime = System.currentTimeMillis()
                    val totalDiarizationTime = diarizationEndTime - diarizationStartTime
                    
                    val totalDuration = segments.sumOf { it.endTime - it.startTime }
                    val outputFile = File(outputPath)
                    
                    updateStatus("✅ Processing completed!")
                    updateStatus("Found ${segments.size} speaker segments")
                    updateStatus("Total duration: ${String.format("%.1f", totalDuration)}s")
                    
                    try {
                        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        downloadsDir?.mkdirs()
                        val publicOutputFile = File(downloadsDir, "user_${selectedUserId}_segments.wav")
                        outputFile.copyTo(publicOutputFile, overwrite = true)
                        updateStatus("📁 Output saved to: ${publicOutputFile.name}")
                        Log.i("MainActivity", "Output file accessible at: ${publicOutputFile.absolutePath}")
                        
                        outputFileUri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            publicOutputFile
                        )
                        
                        withContext(Dispatchers.Main) {
                            // Display the total diarization time
                            val timeInSeconds = totalDiarizationTime / 1000.0
                            diarizationTimeDisplay.text = "⏱️ Diarization completed in ${String.format("%.1f", timeInSeconds)}s"
                            diarizationTimeDisplay.visibility = View.VISIBLE
                            
                            // Show play button and sentiment analysis button like DiarizationTest
                            btnPlayOutput.visibility = View.VISIBLE
                            btnPlayOutput.isEnabled = true
                            btnPlayOutput.text = "▶️ Play Extracted Audio"
                            btnAnalyzeSentiment.visibility = View.VISIBLE
                            btnAnalyzeSentiment.isEnabled = true
                        }
                        
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Could not copy to public directory: ${e.message}")
                        updateStatus("⚠️ File created in app internal storage")
                        outputFileUri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            outputFile
                        )
                        withContext(Dispatchers.Main) {
                            // Still display diarization time even if file copy fails
                            val timeInSeconds = totalDiarizationTime / 1000.0
                            diarizationTimeDisplay.text = "⏱️ Diarization completed in ${String.format("%.1f", timeInSeconds)}s"
                            diarizationTimeDisplay.visibility = View.VISIBLE
                            
                            btnPlayOutput.visibility = View.VISIBLE
                            btnPlayOutput.isEnabled = true
                            btnAnalyzeSentiment.visibility = View.VISIBLE
                            btnAnalyzeSentiment.isEnabled = true
                        }
                    }
                } else {
                    updateStatus("⚠️ No matching speaker segments found")
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing custom files: ${e.message}", e)
                updateStatus("❌ Error: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnCustomAudio.isEnabled = true
                    updateProcessButtonState()
                }
            }
        }
    }
    
    // Simple sentiment analysis using AudioSentimentAnalyzer's 3 tasks
    private fun performSentimentAnalysisOnProcessedAudio() {
        lifecycleScope.launch {
            try {
                // Set initial loading state
                withContext(Dispatchers.Main) {
                    sentimentAnalysisStatus.text = "📊 Loading sentiment analysis data..."
                    sentimentAnalysisStatus.visibility = View.VISIBLE
                }
                
                val startTime = System.currentTimeMillis()
                val sentimentStore = SentimentAnalysisDataStore(this@MainActivity)
                
                // Ensure data is initialized before accessing
                sentimentStore.initializeDefaultSentimentData()
                
                // Debug: Check current user selection
                Log.i("MainActivity", "🔍 Current selected user - Name: '$selectedUserName', ID: '$selectedUserId'")
                
                // If no user is selected, default to first user for demo
                val targetUserId = if (selectedUserId.isEmpty()) {
                    Log.w("MainActivity", "⚠️ No user selected, defaulting to user 1001")
                    "1001"
                } else {
                    selectedUserId
                }
                
                // Get sentiment analysis data from the data store
                val userResults = sentimentStore.getSentimentResultsForUser(targetUserId)
                val recentResults = sentimentStore.getRecentSentimentResults(7) // Last 7 days
                val userStats = sentimentStore.getSentimentStatsForUser(targetUserId, 30) // Last 30 days
                val highRiskResults = sentimentStore.getNegativeSpiralResults()
                
                // Get all results for debugging
                val allResults = try {
                    sentimentStore.sentimentResults.first()
                } catch (e: Exception) {
                    emptyList<SentimentAnalysisResult>()
                }
                
                val loadTime = System.currentTimeMillis() - startTime
                
                Log.i("MainActivity", "🔍 Sentiment data loaded - Total: ${allResults.size}, User: $targetUserId, Results: ${userResults.size}, Recent: ${recentResults.size}")
                
                // Create display content
                val displayContent = if (allResults.isEmpty()) {
                    buildDebugEmptyStateDisplay(targetUserId)
                } else if (userResults.isEmpty()) {
                    buildDebugNoUserDataDisplay(targetUserId, allResults)
                } else {
                    buildSentimentDataDisplay(
                        userResults, 
                        userStats, 
                        recentResults, 
                        highRiskResults
                    )
                }
                
                // Update UI on main thread - do this LAST to avoid race conditions
                withContext(Dispatchers.Main) {
                    sentimentProcessingTimeDisplay.text = "⏱️ Data loaded in ${loadTime}ms"
                    sentimentProcessingTimeDisplay.visibility = View.VISIBLE
                    
                    sentimentAnalysisStatus.text = displayContent
                    sentimentAnalysisStatus.visibility = View.VISIBLE
                    
                    // Show nudge generation button after sentiment analysis is displayed
                    if (userResults.isNotEmpty()) {
                        btnNudgeGeneration.visibility = View.VISIBLE
                        btnNudgeGeneration.isEnabled = true
                    }
                    
                    Log.i("MainActivity", "✅ Sentiment data display updated with ${displayContent.length} characters")
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading sentiment data: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    sentimentAnalysisStatus.text = "❌ ERROR: ${e.message}\n\nStack trace: ${e.stackTrace.take(3).joinToString("\n")}"
                    sentimentAnalysisStatus.visibility = View.VISIBLE
                }
            }
        }
    }
    
    /**
     * Update sentiment analysis status display
     */
    private fun updateSentimentStatus(message: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                sentimentAnalysisStatus.text = message
                sentimentAnalysisStatus.visibility = View.VISIBLE
                Log.i("MainActivity", "Sentiment Status: $message")
            }
        }
    }
    
    /**
     * Generate personalized nudge based on user profile and sentiment data
     */
    private fun performNudgeGeneration() {
        lifecycleScope.launch {
            try {
                Log.i("MainActivity", "🎯 Starting nudge generation...")
                
                // Set initial loading state
                withContext(Dispatchers.Main) {
                    nudgeGenerationStatus.text = "🧠 Generating personalized nudge..."
                    nudgeGenerationStatus.visibility = View.VISIBLE
                    btnNudgeGeneration.isEnabled = false
                }
                
                val startTime = System.currentTimeMillis()
                
                // If no user is selected, default to first user for demo
                val targetUserId = if (selectedUserId.isEmpty()) {
                    Log.w("MainActivity", "⚠️ No user selected for nudge generation, defaulting to user 1001")
                    "1001"
                } else {
                    selectedUserId
                }
                
                Log.i("MainActivity", "🔍 Generating nudge for user: $targetUserId")
                
                // Get sentiment data (most important for nudge generation)
                val sentimentData = getSentimentData(targetUserId)
                Log.i("MainActivity", "📊 Sentiment data: ${sentimentData?.primaryEmotion ?: "none"}")
                
                // Create a simplified prompt to avoid crashes
                val prompt = createSimplifiedNudgePrompt(targetUserId, sentimentData)
                
                Log.i("MainActivity", "📝 Created simplified prompt (${prompt.length} characters)")
                
                // Call Gemma model for nudge generation with error handling
                val nudgeResult = callGemmaForNudgeGenerationSafe(prompt)
                
                val loadTime = System.currentTimeMillis() - startTime
                
                // Update UI with the generated nudge
                withContext(Dispatchers.Main) {
                    nudgeGenerationStatus.text = "🎯 Personalized Nudge:\n\n$nudgeResult\n\n⏱️ Generated in ${loadTime}ms"
                    nudgeGenerationStatus.visibility = View.VISIBLE
                    btnNudgeGeneration.isEnabled = true
                    Log.i("MainActivity", "✅ Nudge generation completed in ${loadTime}ms")
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "💥 CRASH in nudge generation: ${e.message}", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    nudgeGenerationStatus.text = "❌ Sorry, nudge generation failed. Please try again.\n\nError: ${e.message}"
                    nudgeGenerationStatus.visibility = View.VISIBLE
                    btnNudgeGeneration.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun getUserProfileData(userId: String): UserProfile? {
        return userProfiles.find { it.userId == userId }
    }
    
    private suspend fun getStartingPointData(userId: String): StartingPointData? {
        return startingPointData.find { it.userId == userId }
    }
    
    private suspend fun getPersonalityData(userId: String): PersonalityPreferencesData? {
        return personalityData.find { it.userId == userId }
    }
    
    private suspend fun getJoyToolkitData(userId: String): JoyToolkitData? {
        return joyToolkitData.find { it.userId == userId }
    }
    
    private suspend fun getSentimentData(userId: String): SentimentAnalysisResult? {
        val sentimentStore = SentimentAnalysisDataStore(this@MainActivity)
        val userResults = sentimentStore.getSentimentResultsForUser(userId)
        return userResults.firstOrNull() // Get the most recent sentiment analysis
    }
    
    private fun getJoyToolkitJson(joyToolkitData: JoyToolkitData?): String {
        return """
    "sight": "${joyToolkitData?.let { it.sensoryToolkit.sight } ?: "Nature"}",
    "sound": "${joyToolkitData?.let { it.sensoryToolkit.sound } ?: "Peaceful"}",
    "hobby": "${joyToolkitData?.let { it.activityToolkit.hobbiesAndPassions } ?: "Reading"}",
    "movement": "${joyToolkitData?.let { it.activityToolkit.powerOfMovement } ?: "Walking"}",
    "mental_reset": "${joyToolkitData?.let { it.mindToolkit.mentalReset } ?: "Deep breathing"}",
    "self_talk": "${joyToolkitData?.let { it.mindToolkit.selfTalk } ?: "I am enough"}",
    "curiosity": "${joyToolkitData?.let { it.mindToolkit.curiosity } ?: "Learning"}",
    "focus_tool": "${joyToolkitData?.let { it.mindToolkit.focusTools } ?: "Meditation"}",
    "values": "${joyToolkitData?.let { it.meaningToolkit.values } ?: "Peace"}"
        """.trimIndent()
    }

    private fun createSimplifiedNudgePrompt(userId: String, sentimentData: SentimentAnalysisResult?): String {
        val startingPointData = startingPointData.find { it.userId == userId }
        val personalityData = personalityData.find { it.userId == userId }
        val joyToolkitData = joyToolkitData.find { it.userId == userId }

        return """
You are Amica — a hyper-personalized emotional wellness AI companion designed to support users with compassionate and science-backed nudges.

Given the following user profile and emotional data, generate one helpful, nudge suggestion in natural language. The nudge should be emotionally intelligent, aligned with the user's emotional state, personality traits, and known preferences. Use supportive and gentle language.

Respond with a short, actionable nudge (2-3 lines) that:
- aligns with user's emotional needs (from sentiment & emotion analysis),
- avoids triggering or judgmental tone (especially in high neuroticism),
- leverages preferred calming or joy-based cues (from Joy Toolkit),
- and helps interrupt a negative spiral if detected.

Here is the user profile:

{
  "depression_severity": "${startingPointData?.getDepressionSeverityLevel() ?: "UNKNOWN"}",
  "big_five": {
    "openness": ${personalityData?.let { 
        when {
            it.weekendChoice == PersonalityPreferencesData.WeekendChoice.SOLO_EXPLORATION -> 0.8
            else -> 0.4
        }
    } ?: 0.5},
    "conscientiousness": ${personalityData?.let {
        when {
            it.vacationChoice == PersonalityPreferencesData.VacationChoice.DETAILED_PLANNING -> 0.8
            else -> 0.4
        }
    } ?: 0.5},
    "extraversion": ${personalityData?.let {
        when {
            it.weekendChoice == PersonalityPreferencesData.WeekendChoice.GROUP_ACTIVITY -> 0.8
            else -> 0.4
        }
    } ?: 0.5},
    "agreeableness": ${personalityData?.let {
        when {
            it.problemSolvingChoice == PersonalityPreferencesData.ProblemSolvingChoice.EMOTIONAL_SUPPORT -> 0.8
            else -> 0.4
        }
    } ?: 0.5},
    "neuroticism": ${personalityData?.let {
        when {
            it.changeResponseChoice == PersonalityPreferencesData.ChangeResponseChoice.ANXIOUS -> 0.8
            else -> 0.4
        }
    } ?: 0.5}
  },
  "joy_toolkit": {
    "sight": "${joyToolkitData?.let { it.sensoryToolkit.sight } ?: "Nature"}",
    "sound": "${joyToolkitData?.let { it.sensoryToolkit.sound } ?: "Peaceful"}",
    "hobby": "${joyToolkitData?.let { it.activityToolkit.hobbiesAndPassions } ?: "Reading"}",
    "movement": "${joyToolkitData?.let { it.activityToolkit.powerOfMovement } ?: "Walking"}",
    "mental_reset": "${joyToolkitData?.let { it.mindToolkit.mentalReset } ?: "Deep breathing"}",
    "self_talk": "${joyToolkitData?.let { it.mindToolkit.selfTalk } ?: "I am enough"}",
    "curiosity": "${joyToolkitData?.let { it.mindToolkit.curiosity } ?: "Learning"}",
    "focus_tool": "${joyToolkitData?.let { it.mindToolkit.focusTools } ?: "Meditation"}",
    "values": "${joyToolkitData?.let { it.meaningToolkit.values } ?: "Peace"}"
  },
  "audio_emotion_analysis": {
    "primary_emotion": "${sentimentData?.primaryEmotion ?: "neutral"}",
    "sentiment": "${sentimentData?.sentiment?.displayName ?: "neutral"}",
    "emotional_intensity": ${when(sentimentData?.emotionalIntensity) {
        null -> 0.5
        EmotionalIntensity.VERY_LOW -> 0.1
        EmotionalIntensity.LOW -> 0.3
        EmotionalIntensity.MODERATE -> 0.5
        EmotionalIntensity.HIGH -> 0.75
        EmotionalIntensity.VERY_HIGH -> 0.85
        else -> 0.5
    }},
    "valence": ${when(sentimentData?.valence) {
        null -> 0.0
        Valence.VERY_NEGATIVE -> -0.75
        Valence.NEGATIVE -> -0.45
        Valence.NEUTRAL -> 0.0
        Valence.POSITIVE -> 0.45
        Valence.VERY_POSITIVE -> 0.75
        else -> 0.0
    }},
    "arousal": ${when(sentimentData?.arousal) {
        null -> 0.5
        Arousal.VERY_LOW -> 0.1
        Arousal.LOW -> 0.3
        Arousal.MEDIUM -> 0.5
        Arousal.HIGH -> 0.75
        Arousal.VERY_HIGH -> 0.85
        else -> 0.5
    }},
    "confidence_score": ${sentimentData?.confidenceScore ?: 0.5}
  },
  "negative_spiral": {
    "is_detected": ${sentimentData?.isNegativeSpiralDetected ?: false},
    "triggers": [${sentimentData?.detectedTriggers?.joinToString(", ") { "\"$it\"" } ?: ""}],
    "themes": [${sentimentData?.keyThemes?.joinToString(", ") { "\"$it\"" } ?: ""}],
    "keywords": [${sentimentData?.importantKeywords?.joinToString(", ") { "\"$it\"" } ?: ""}]
  }
}

Output the suggested nudge in plain text only.
        """.trimIndent()
    }
    
    private fun formatNudgeGenerationPrompt(
        userProfile: UserProfile?,
        startingPointData: StartingPointData?,
        personalityData: PersonalityPreferencesData?,
        joyToolkitData: JoyToolkitData?,
        sentimentData: SentimentAnalysisResult?
    ): String {
        return """
You are Amica — a hyper-personalized emotional wellness AI companion designed to support users with compassionate and science-backed nudges.

Given the following user profile and emotional data, generate one helpful, nudge suggestion in natural language. The nudge should be emotionally intelligent, aligned with the user's emotional state, personality traits, and known preferences. Use supportive and gentle language.

Respond with a short, actionable nudge (2-3 lines) that:
- aligns with user's emotional needs (from sentiment & emotion analysis),
- avoids triggering or judgmental tone (especially in high neuroticism),
- leverages preferred calming or joy-based cues (from Joy Toolkit),
- and helps interrupt a negative spiral if detected.

Here is the user profile:

{
  "depression_severity": "${startingPointData?.getDepressionSeverityLevel() ?: "UNKNOWN"}",
  "big_five": {
    "openness": 0.5,
    "conscientiousness": 0.5,
    "extraversion": 0.5,
    "agreeableness": 0.5,
    "neuroticism": 0.5
  },
  "joy_toolkit": {
    "sight": "${joyToolkitData?.sensoryToolkit?.sight ?: "Nature"}",
    "sound": "${joyToolkitData?.sensoryToolkit?.sound ?: "Peaceful"}",
    "hobby": "${joyToolkitData?.activityToolkit?.hobbiesAndPassions ?: "Reading"}",
    "movement": "${joyToolkitData?.activityToolkit?.powerOfMovement ?: "Walking"}",
    "mental_reset": "${joyToolkitData?.mindToolkit?.mentalReset ?: "Deep breathing"}",
    "self_talk": "${joyToolkitData?.mindToolkit?.selfTalk ?: "I am enough"}",
    "curiosity": "${joyToolkitData?.mindToolkit?.curiosity ?: "Learning"}",
    "focus_tool": "${joyToolkitData?.mindToolkit?.focusTools ?: "Meditation"}",
    "values": "${joyToolkitData?.meaningToolkit?.values ?: "Peace"}"
  },
  "audio_emotion_analysis": {
    "primary_emotion": "${sentimentData?.primaryEmotion ?: "neutral"}",
    "sentiment": "${sentimentData?.sentiment?.displayName ?: "neutral"}",
    "emotional_intensity": ${when(sentimentData?.emotionalIntensity) {
        null -> 0.5
        EmotionalIntensity.VERY_LOW -> 0.1
        EmotionalIntensity.LOW -> 0.3
        EmotionalIntensity.MODERATE -> 0.5
        EmotionalIntensity.HIGH -> 0.75
        EmotionalIntensity.VERY_HIGH -> 0.85
        else -> 0.5
    }},
    "valence": ${when(sentimentData?.valence) {
        null -> 0.0
        Valence.VERY_NEGATIVE -> -0.75
        Valence.NEGATIVE -> -0.45
        Valence.NEUTRAL -> 0.0
        Valence.POSITIVE -> 0.45
        Valence.VERY_POSITIVE -> 0.75
        else -> 0.0
    }},
    "arousal": ${when(sentimentData?.arousal) {
        null -> 0.5
        Arousal.VERY_LOW -> 0.1
        Arousal.LOW -> 0.3
        Arousal.MEDIUM -> 0.5
        Arousal.HIGH -> 0.75
        Arousal.VERY_HIGH -> 0.85
        else -> 0.5
    }},
    "confidence_score": ${sentimentData?.confidenceScore ?: 0.5}
  },
  "negative_spiral": {
    "is_detected": ${sentimentData?.isNegativeSpiralDetected ?: false},
    "triggers": [${sentimentData?.detectedTriggers?.joinToString(", ") { "\"$it\"" } ?: ""}],
    "themes": [${sentimentData?.keyThemes?.joinToString(", ") { "\"$it\"" } ?: ""}],
    "keywords": [${sentimentData?.importantKeywords?.joinToString(", ") { "\"$it\"" } ?: ""}]
  }
}

Output the suggested nudge in plain text only.
        """.trimIndent()
    }
    
    private suspend fun callGemmaForNudgeGenerationSafe(prompt: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.i("MainActivity", "🎤 Calling Gemma for nudge...")
            
            // Basic validation
            if (!::audioSentimentAnalyzer.isInitialized || !audioSentimentAnalyzer.isModelInitialized()) {
                Log.e("MainActivity", "❌ AudioSentimentAnalyzer not initialized")
                return@withContext "The AI companion is not ready yet. Please try again in a moment."
            }
            
            // Log and call Gemma
            Log.i("MainActivity", "📝 Sending prompt to Gemma:\n$prompt")
            val gemmaResponse = audioSentimentAnalyzer.nudgeGeneration(prompt)
            
            // Return response or fallback
            if (gemmaResponse.isNullOrBlank()) {
                Log.w("MainActivity", "⚠️ No response from Gemma")
                "I'm here to support you. Take a moment to breathe and remember that difficult feelings are temporary. 💙"
            } else {
                gemmaResponse
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ CRASH in nudge generation", e)
            Log.e("MainActivity", "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Log.e("MainActivity", "Cause: ${e.cause?.message ?: "unknown"}")
            if (e is OutOfMemoryError) {
                Log.e("MainActivity", "💥 Out of memory error!")
                return@withContext "The AI model needs more memory. Please try again in a moment. 💙"
            }
            if (e is IllegalStateException) {
                Log.e("MainActivity", "⚠️ Illegal state: ${e.message}")
                return@withContext "The AI model is not in the right state. Please restart the app. 💙"
            }
            "I'm here to support you. Take a moment to breathe and remember that you matter. 💙"
        }
    }
    
    /**
     * Build display for when no sentiment data is available
     */
    private fun buildEmptyStateDisplay(): String {
        return """
            📊 SENTIMENT ANALYSIS DATA STORE
            
            👤 User: $selectedUserName (ID: $selectedUserId)
            
            📝 STATUS: No sentiment analysis data found
            
            💡 SUGGESTIONS:
            • Process some audio files first
            • Run sentiment analysis on recorded conversations
            • Check that data is being saved properly
            
            🔍 WHAT TO EXPECT:
            • Emotional state tracking
            • Negative spiral detection
            • Mood trends over time
            • Risk assessment alerts
        """.trimIndent()
    }
    
    /**
     * Debug display when no data exists at all
     */
    private fun buildDebugEmptyStateDisplay(targetUserId: String): String {
        return """
            📊 SENTIMENT ANALYSIS DEBUG
            
            👤 Target User: $targetUserId
            🔍 Selected User: $selectedUserName (ID: $selectedUserId)
            
            ❌ STATUS: NO DATA IN STORE
            
            🔧 DEBUGGING INFO:
            • Data store appears to be empty
            • Initialization may have failed
            • Check logs for initialization errors
            
            💡 This means the default sentiment data wasn't loaded properly.
        """.trimIndent()
    }
    
    /**
     * Debug display when data exists but not for selected user
     */
    private fun buildDebugNoUserDataDisplay(targetUserId: String, allResults: List<SentimentAnalysisResult>): String {
        val availableUserIds = allResults.map { it.userId }.distinct().sorted()
        val sampleResult = allResults.firstOrNull()
        
        return """
            📊 SENTIMENT ANALYSIS DEBUG
            
            👤 Target User: $targetUserId
            🔍 Selected User: $selectedUserName (ID: $selectedUserId)
            
            ⚠️ STATUS: USER DATA NOT FOUND
            
            📋 AVAILABLE DATA:
            • Total records: ${allResults.size}
            • Available user IDs: ${availableUserIds.joinToString(", ")}
            
            🔍 SAMPLE RECORD:
            ${if (sampleResult != null) {
                "• User: ${sampleResult.userId}\n• Emotion: ${sampleResult.primaryEmotion}\n• Date: ${sampleResult.dateTime}"
            } else {
                "• No sample available"
            }}
            
            💡 Try selecting one of the available users: ${availableUserIds.joinToString(", ")}
        """.trimIndent()
    }
    
    /**
     * Build comprehensive sentiment data display
     */
    private fun buildSentimentDataDisplay(
        userResults: List<SentimentAnalysisResult>,
        userStats: SentimentAnalysisStats,
        recentResults: List<SentimentAnalysisResult>,
        highRiskResults: List<SentimentAnalysisResult>
    ): String {
        val latest = userResults.lastOrNull()
        val userHighRisk = highRiskResults.filter { it.userId == selectedUserId }
        
        return buildString {
            appendLine("📊 SENTIMENT ANALYSIS DASHBOARD")
            appendLine()
            appendLine("👤 User: $selectedUserName (ID: $selectedUserId)")
            appendLine()
            
            // Latest Analysis
            appendLine("🕐 LATEST ANALYSIS:")
            if (latest != null) {
                appendLine("• ${latest.getFormattedDateTime()}")
                                 appendLine("• Primary Emotion: ${latest.primaryEmotion.replaceFirstChar { it.uppercase() }}")
                appendLine("• Sentiment: ${latest.sentiment.displayName}")
                appendLine("• Confidence: ${String.format("%.1f%%", latest.confidenceScore * 100)}")
                if (latest.isNegativeSpiralDetected) {
                    appendLine("• ⚠️ NEGATIVE SPIRAL DETECTED")
                    appendLine("  Triggers: ${latest.detectedTriggers.joinToString(", ")}")
                }
                appendLine("• Audio: ${latest.audioFileName ?: "Unknown"}")
            } else {
                appendLine("• No recent analysis available")
            }
            appendLine()
            
            // Statistics
            appendLine("📈 30-DAY STATISTICS:")
            appendLine("• Total Analyses: ${userStats.totalAnalyses}")
            appendLine("• Negative Spirals: ${userStats.negativeSpiralCount}")
            appendLine("• Average Confidence: ${String.format("%.1f%%", userStats.averageConfidence * 100)}")
            appendLine("• Most Common Emotion: ${userStats.mostCommonEmotion}")
                         appendLine("• Trend: ${userStats.trendDirection.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}")
            appendLine()
            
            // Recent Activity
            appendLine("📅 RECENT ACTIVITY (7 days):")
            val userRecentResults = recentResults.filter { it.userId == selectedUserId }.take(3)
            if (userRecentResults.isNotEmpty()) {
                userRecentResults.forEach { result ->
                    appendLine("• ${result.getFormattedDateTime()}: ${result.getSummary()}")
                }
                if (userRecentResults.size < recentResults.filter { it.userId == selectedUserId }.size) {
                    val remaining = recentResults.filter { it.userId == selectedUserId }.size - userRecentResults.size
                    appendLine("• ... and $remaining more")
                }
            } else {
                appendLine("• No recent activity")
            }
            appendLine()
            
            // Risk Assessment
            appendLine("🚨 RISK ASSESSMENT:")
            if (userHighRisk.isNotEmpty()) {
                appendLine("• ⚠️ ${userHighRisk.size} high-risk detection(s)")
                val latestRisk = userHighRisk.lastOrNull()
                if (latestRisk != null) {
                    appendLine("• Latest: ${latestRisk.getFormattedDateTime()}")
                    appendLine("• Reasoning: ${latestRisk.reasoning.take(100)}...")
                }
            } else {
                appendLine("• ✅ No high-risk patterns detected")
            }
            appendLine()
            
            // Common Triggers
            if (userStats.mostCommonTriggers.isNotEmpty()) {
                appendLine("🎯 COMMON TRIGGERS:")
                userStats.mostCommonTriggers.take(3).forEach { trigger ->
                    appendLine("• $trigger")
                }
                appendLine()
            }
            
            // Key Themes
            val allThemes = userResults.flatMap { it.keyThemes }.distinct()
            if (allThemes.isNotEmpty()) {
                appendLine("💭 KEY THEMES:")
                allThemes.take(5).forEach { theme ->
                    appendLine("• $theme")
                }
                         }
         }
     }
     


    // Add play audio functionality same as DiarizationTest
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var isPlaying = false
    
    private fun playOutputAudio() {
        outputFileUri?.let { uri ->
            if (isPlaying) {
                stopAudioPlayback()
                return
            }
            
            try {
                Log.i("MainActivity", "Starting audio playback for: $uri")
                lifecycleScope.launch {
                    updateStatus("🎵 Preparing audio playback...")
                }
                
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(this@MainActivity, uri)
                    setOnPreparedListener { mp ->
                        mp.start()
                        runOnUiThread {
                            this@MainActivity.isPlaying = true
                            btnPlayOutput.text = "⏸️ Stop Audio"
                            val duration = mp.duration / 1000
                            lifecycleScope.launch {
                                updateStatus("🎵 Playing audio (${duration}s)...")
                            }
                        }
                        Log.i("MainActivity", "Started audio playback")
                    }
                    setOnCompletionListener {
                        stopAudioPlayback()
                        runOnUiThread {
                            lifecycleScope.launch {
                                updateStatus("✅ Audio playback completed")
                            }
                        }
                        Log.i("MainActivity", "Audio playback completed")
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("MainActivity", "MediaPlayer error: what=$what, extra=$extra")
                        runOnUiThread {
                            lifecycleScope.launch {
                                updateStatus("❌ Audio playback error")
                            }
                            resetPlayButton()
                        }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting audio playback: ${e.message}", e)
                runOnUiThread {
                    lifecycleScope.launch {
                        updateStatus("❌ Error playing audio: ${e.message}")
                    }
                    resetPlayButton()
                }
            }
        } ?: run {
            Log.w("MainActivity", "No output file URI available")
            runOnUiThread {
                lifecycleScope.launch {
                    updateStatus("⚠️ No audio file available to play")
                }
            }
        }
    }
    
    private fun stopAudioPlayback() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.w("MainActivity", "Error stopping MediaPlayer: ${e.message}")
            }
        }
        mediaPlayer = null
        isPlaying = false
        runOnUiThread {
            resetPlayButton()
        }
    }
    
    private fun resetPlayButton() {
        btnPlayOutput.text = "▶️ Play Extracted Audio"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up MediaPlayer resources
        stopAudioPlayback()
    }
    
    // Helper methods from DiarizationTest
    private suspend fun copyUriToInternalStorage(uri: Uri, filename: String): String {
        return withContext(Dispatchers.IO) {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, filename)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        }
    }

    private suspend fun copyModelToInternalStorage(): String {
        return withContext(Dispatchers.IO) {
            val modelPath = "ecapa_model_dynamic_quantized.onnx"
            val inputStream = assets.open(modelPath)
            val file = File(filesDir, modelPath)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file.absolutePath
        }
    }
    
    private suspend fun updateStatus(message: String) {
        withContext(Dispatchers.Main) {
            selectedAudioInfo.text = message
            selectedAudioInfo.visibility = View.VISIBLE
            Log.i("MainActivity", message)
        }
    }
    
    private fun showAudioProcessingSection() {
        audioProcessingSection.visibility = View.VISIBLE
    }
    
    private fun hideAudioProcessingSection() {
        audioProcessingSection.visibility = View.GONE
        enrollmentUri = null
        meetingUri = null
        enrollmentFileStatus.text = "No file selected"
        enrollmentFileStatus.setTextColor(getColor(android.R.color.darker_gray))
        meetingFileStatus.text = "No file selected"
        meetingFileStatus.setTextColor(getColor(android.R.color.darker_gray))
        diarizationTimeDisplay.visibility = View.GONE
        btnPlayOutput.visibility = View.GONE
        btnAnalyzeSentiment.visibility = View.GONE
        btnNudgeGeneration.visibility = View.GONE
        sentimentAnalysisStatus.visibility = View.GONE
        nudgeGenerationStatus.visibility = View.GONE
        sentimentProcessingTimeDisplay.visibility = View.GONE
        updateProcessButtonState()
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            bytes >= 1024 -> "${bytes / 1024}KB"
            else -> "${bytes}B"
        }
    }
    

    
    private fun setupUserSpinner() {
        val userNames = mutableListOf<String>()
        userNames.add("Select User Profile") // Default item
        userNames.addAll(userProfiles.map { it.userName })
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, userNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        userSpinner.adapter = adapter
        
        Log.i("MainActivity", "User spinner set up with ${userProfiles.size} users")
    }
    
    private fun displayUserData(userId: String) {
        // Find starting point data for user
        val userStartingPoint = startingPointData.find { it.userId == userId }
        if (userStartingPoint != null) {
            val score = userStartingPoint.calculateDepressionScore()
            val severity = userStartingPoint.getDepressionSeverityLevel()
            
            severityLevel.text = "Severity: $severity"
            depressionScore.text = "Score: $score"
            
            // Set severity color
            val severityColor = when (severity) {
                StartingPointData.DepressionSeverityLevel.NORMAL -> "#4CAF50" // Green
                StartingPointData.DepressionSeverityLevel.MILD -> "#FFC107" // Amber
                StartingPointData.DepressionSeverityLevel.MODERATE -> "#FF9800" // Orange
                StartingPointData.DepressionSeverityLevel.SEVERE -> "#F44336" // Red
                StartingPointData.DepressionSeverityLevel.EXTREMELY_SEVERE -> "#9C27B0" // Purple
            }
            severityLevel.setTextColor(android.graphics.Color.parseColor(severityColor))
            
            startingPointCard.visibility = View.VISIBLE
        } else {
            startingPointCard.visibility = View.GONE
        }
        
        // Find personality data for user
        val userPersonality = personalityData.find { it.userId == userId }
        if (userPersonality != null) {
            val personalityText = buildString {
                // Show preference descriptions
                append("PREFERENCES:\n")
                append("Weekend: ${getWeekendDescription(userPersonality.weekendChoice)}\n")
                append("Vacation: ${getVacationDescription(userPersonality.vacationChoice)}\n")
                append("Me Time: ${getMeTimeDescription(userPersonality.meTimeChoice)}\n")
                append("Problem Solving: ${getProblemSolvingDescription(userPersonality.problemSolvingChoice)}\n")
                append("Change Response: ${getChangeResponseDescription(userPersonality.changeResponseChoice)}\n\n")
                
                // Show OCEAN scores
                append("OCEAN PERSONALITY SCORES:\n")
                val traitScores = userPersonality.getPersonalityTraits()
                traitScores.forEach { traitScore ->
                    val percentage = (traitScore.score * 100).toInt()
                    append("${traitScore.trait.name}: ${percentage}%\n")
                    append("${traitScore.getInterpretation()}\n\n")
                }
            }
            personalityTraits.text = personalityText
            personalityCard.visibility = View.VISIBLE
        } else {
            personalityTraits.text = "Personality data not available for this user"
            personalityCard.visibility = View.VISIBLE
        }
        
        // Find joy toolkit data for user
        val userJoyToolkit = joyToolkitData.find { it.userId == userId }
        if (userJoyToolkit != null) {
            val joyToolkitText = buildString {
                append("JOY TOOLKIT:\n\n")
                
                append("🎨 SENSORY TOOLKIT:\n")
                append("Sight: ${userJoyToolkit.sensoryToolkit.sight}\n")
                append("Sound: ${userJoyToolkit.sensoryToolkit.sound}\n\n")
                
                append("⚡ ACTIVITY TOOLKIT:\n")
                append("Hobbies & Passions: ${userJoyToolkit.activityToolkit.hobbiesAndPassions}\n")
                append("Power of Movement: ${userJoyToolkit.activityToolkit.powerOfMovement}\n\n")
                
                append("🧠 MIND TOOLKIT:\n")
                append("Mental Reset: ${userJoyToolkit.mindToolkit.mentalReset}\n")
                append("Self-Talk: ${userJoyToolkit.mindToolkit.selfTalk}\n")
                append("Curiosity: ${userJoyToolkit.mindToolkit.curiosity}\n")
                append("Focus Tools: ${userJoyToolkit.mindToolkit.focusTools}\n\n")
                
                append("💫 MEANING TOOLKIT:\n")
                append("Values: ${userJoyToolkit.meaningToolkit.values}")
            }
            personalityTraits.text = personalityTraits.text.toString() + "\n\n" + joyToolkitText
        }
    }
    
    private fun getWeekendDescription(choice: PersonalityPreferencesData.WeekendChoice): String {
        return when (choice) {
            PersonalityPreferencesData.WeekendChoice.SOLO_EXPLORATION -> "Solo exploration and self-discovery"
            PersonalityPreferencesData.WeekendChoice.GROUP_ACTIVITY -> "Social group activities and interactions"
        }
    }
    
    private fun getVacationDescription(choice: PersonalityPreferencesData.VacationChoice): String {
        return when (choice) {
            PersonalityPreferencesData.VacationChoice.DETAILED_PLANNING -> "Detailed planning and itineraries"
            PersonalityPreferencesData.VacationChoice.SPONTANEOUS -> "Spontaneous and flexible approach"
        }
    }
    
    private fun getMeTimeDescription(choice: PersonalityPreferencesData.MeTimeChoice): String {
        return when (choice) {
            PersonalityPreferencesData.MeTimeChoice.CREATIVE_PROJECT -> "Creative projects and artistic pursuits"
            PersonalityPreferencesData.MeTimeChoice.ORGANIZING -> "Organizing and structuring environment"
        }
    }
    
    private fun getProblemSolvingDescription(choice: PersonalityPreferencesData.ProblemSolvingChoice): String {
        return when (choice) {
            PersonalityPreferencesData.ProblemSolvingChoice.EMOTIONAL_SUPPORT -> "Emotional support and empathy focused"
            PersonalityPreferencesData.ProblemSolvingChoice.PRACTICAL_SOLUTIONS -> "Practical solutions and logical analysis"
        }
    }
    
    private fun getChangeResponseDescription(choice: PersonalityPreferencesData.ChangeResponseChoice): String {
        return when (choice) {
            PersonalityPreferencesData.ChangeResponseChoice.ADAPTABLE -> "Adaptable and emotionally stable"
            PersonalityPreferencesData.ChangeResponseChoice.ANXIOUS -> "Anxious and sensitive to change"
        }
    }
    
    private fun hideUserData() {
        startingPointCard.visibility = View.GONE
        personalityCard.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_onboarding -> {
                startActivity(android.content.Intent(this, AmeekaaOnboarding::class.java))
                true
            }
            R.id.menu_mvp_test -> {
                startActivity(android.content.Intent(this, DiarizationTest::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

/**
 * Data classes for structured analysis results
 */
@Serializable
data class AudioAnalysisResult(
    val emotionSentiment: EmotionSentimentResult,
    val negativeSpiralDetection: NegativeSpiralResult,
    val topicKeywordExtraction: TopicExtractionResult
)

@Serializable
data class EmotionSentimentResult(
    val primaryEmotion: String,
    val sentiment: String,
    val confidenceScore: Float
)

@Serializable
data class NegativeSpiralResult(
    val isNegativeSpiralDetected: Boolean,
    val detectedTriggers: List<String>,
    val reasoning: String
)

@Serializable
data class TopicExtractionResult(
    val keyThemes: List<String>,
    val importantKeywords: List<String>
) 


