package com.ameekaa.app.sentiment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ameekaa.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for performing sentiment analysis on processed audio using Gemma3n
 * Integrates with the main Amica app to analyze speaker-segmented audio files
 */
class SentimentActivity : AppCompatActivity() {
    
    private val TAG = "SentimentActivity"
    
    // UI Components
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDownloadModel: Button
    private lateinit var btnAnalyzeAudio: Button
    private lateinit var btnSelectAudio: Button
    private lateinit var resultText: TextView
    private lateinit var btnBack: Button
    
    // Sentiment analysis components
    private lateinit var kaggleDownloader: KaggleModelDownloader
    private lateinit var sentimentAnalyzer: SentimentAnalyzer
    
    // Selected audio file for analysis
    private var selectedAudioFile: File? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sentiment)
        
        initializeViews()
        initializeComponents()
        checkInitialState()
    }
    
    private fun initializeViews() {
        statusText = findViewById(R.id.status_text)
        progressBar = findViewById(R.id.progress_bar)
        btnDownloadModel = findViewById(R.id.btn_download_model)
        btnAnalyzeAudio = findViewById(R.id.btn_analyze_audio)
        btnSelectAudio = findViewById(R.id.btn_select_audio)
        resultText = findViewById(R.id.result_text)
        btnBack = findViewById(R.id.btn_back)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        btnDownloadModel.setOnClickListener { downloadModel() }
        btnAnalyzeAudio.setOnClickListener { analyzeSelectedAudio() }
        btnSelectAudio.setOnClickListener { selectAudioFile() }
        btnBack.setOnClickListener { finish() }
    }
    
    private fun initializeComponents() {
        kaggleDownloader = KaggleModelDownloader(this)
        sentimentAnalyzer = SentimentAnalyzer(this, ModelConfig())
    }
    
    private fun checkInitialState() {
        updateStatus("🧠 Gemma3n Audio Sentiment Analyzer")
        
        // Check if model is available (either from external storage or downloaded)
        val sentimentModelInfo = sentimentAnalyzer.getModelInfo()
        
        Log.d(TAG, "Sentiment analyzer model info: isDownloaded=${sentimentModelInfo.isDownloaded}, path=${sentimentModelInfo.modelPath}")
        
        if (sentimentModelInfo.isDownloaded) {
            if (sentimentModelInfo.modelPath.contains("assets") || sentimentModelInfo.modelPath.contains("Download") || sentimentModelInfo.modelPath.contains("files")) {
                updateStatus("✅ Gemma3n model ready from external storage (${sentimentModelInfo.modelName})")
                btnDownloadModel.text = "✅ Model Ready"
            } else {
                updateStatus("✅ Gemma3n model ready (${sentimentModelInfo.modelSize / 1024 / 1024} MB)")
                btnDownloadModel.text = "✅ Model Downloaded"
            }
            btnDownloadModel.isEnabled = false
            btnSelectAudio.isEnabled = true
            
            // Try to initialize the sentiment analyzer
            initializeSentimentAnalyzer()
        } else {
            updateStatus("📥 No model found - place your gemma-3n-E2B-it-int4.task file in Downloads folder, or download from Kaggle")
            btnDownloadModel.isEnabled = true
            btnDownloadModel.text = "📥 Download from Kaggle"
            btnSelectAudio.isEnabled = false
            btnAnalyzeAudio.isEnabled = false
        }
        
        // Check if audio file was passed from MainActivity
        checkForProvidedAudioFile()
    }
    
    private fun checkForProvidedAudioFile() {
        val audioPath = intent.getStringExtra("audio_file_path")
        if (!audioPath.isNullOrEmpty()) {
            val audioFile = File(audioPath)
            if (audioFile.exists()) {
                selectedAudioFile = audioFile
                updateStatus("🎵 Audio file provided: ${audioFile.name}")
                btnSelectAudio.text = "Audio Selected: ${audioFile.name}"
                if (sentimentAnalyzer.isInitialized()) {
                    btnAnalyzeAudio.isEnabled = true
                }
            }
        }
    }
    
    private fun downloadModel() {
        if (!kaggleDownloader.hasEnoughSpace()) {
            Toast.makeText(this, "Not enough storage space (3GB required)", Toast.LENGTH_LONG).show()
            return
        }
        
        // Check if kaggle.json is available
        if (!kaggleDownloader.hasKaggleCredentials()) {
            showKaggleCredentialsInstructions()
            return
        }
        
        // Show information about Kaggle download
        showKaggleDownloadDialog()
    }
    
    private fun showKaggleCredentialsInstructions() {
        val instructions = """
            To download the Gemma model from Kaggle, you need:
            
            1. A Kaggle account with API access
            2. Your kaggle.json file in the app's assets
            
            Steps to setup:
            1. Go to https://www.kaggle.com/settings/account
            2. Scroll to "API" section
            3. Click "Create New Token" 
            4. Save the kaggle.json file
            5. Place it in the app's assets directory
            
            The kaggle.json should contain:
            {
              "username": "your_username",
              "key": "your_api_key"
            }
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("🔑 Kaggle API Setup Required")
            .setMessage(instructions)
            .setPositiveButton("Copy Instructions") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Kaggle Setup", instructions)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Instructions copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showKaggleDownloadDialog() {
        AlertDialog.Builder(this)
            .setTitle("📥 Download from Kaggle")
            .setMessage("This will download the Gemma 3n model from Kaggle:\n\n" +
                       "Model: google/gemma-3n/tfLite/gemma-3n-e2b-it-int4\n" +
                       "Size: ~3GB (INT4 quantized)\n" +
                       "Source: Kaggle Models\n\n" +
                       "Your kaggle.json credentials will be used for authentication.")
            .setPositiveButton("Start Download") { _, _ ->
                startKaggleModelDownload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startKaggleModelDownload() {
        btnDownloadModel.isEnabled = false
        progressBar.visibility = View.VISIBLE
        updateStatus("📥 Downloading Gemma3n model from Kaggle...")
        
        lifecycleScope.launch {
            kaggleDownloader.downloadModel().collect { progress ->
                withContext(Dispatchers.Main) {
                    when (progress) {
                        is DownloadProgress.Starting -> {
                            updateStatus("🚀 Starting Kaggle download...")
                        }
                        is DownloadProgress.Progress -> {
                            val mbDownloaded = progress.bytesDownloaded / 1024 / 1024
                            val mbTotal = progress.totalBytes / 1024 / 1024
                            updateStatus("📥 Downloading: $mbDownloaded MB / $mbTotal MB (${progress.percentage}%)")
                        }
                        is DownloadProgress.Success -> {
                            progressBar.visibility = View.GONE
                            updateStatus("✅ Model downloaded successfully from Kaggle!")
                            btnDownloadModel.text = "✅ Model Downloaded"
                            btnSelectAudio.isEnabled = true
                            initializeSentimentAnalyzer()
                        }
                        is DownloadProgress.Error -> {
                            progressBar.visibility = View.GONE
                            updateStatus("❌ Download failed")
                            btnDownloadModel.isEnabled = true
                            btnDownloadModel.text = "🔄 Retry Download"
                            
                            // Show error dialog with retry option
                            AlertDialog.Builder(this@SentimentActivity)
                                .setTitle("Download Failed")
                                .setMessage("${progress.message}\n\nPlease check:\n" +
                                           "- Internet connection\n" +
                                           "- Kaggle credentials in kaggle.json\n" +
                                           "- Available storage space")
                                .setPositiveButton("Retry") { _, _ ->
                                    downloadModel()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }
            }
        }
    }
    
    private fun initializeSentimentAnalyzer() {
        lifecycleScope.launch {
            updateStatus("🔧 Initializing Gemma3n analyzer...")
            
            sentimentAnalyzer.initialize().fold(
                onSuccess = {
                    updateStatus("✅ Gemma3n analyzer is ready")
                    btnAnalyzeAudio.isEnabled = selectedAudioFile != null
                },
                onFailure = { error ->
                    updateStatus("❌ Failed to initialize analyzer: ${error.message}")
                    Log.e(TAG, "Sentiment analyzer initialization failed", error)
                }
            )
        }
    }
    
    private fun selectAudioFile() {
        // Look for audio files in common locations
        val possibleLocations = listOf(
            File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "user_custom_segments.wav"),
            File(filesDir, "user_custom_segments.wav"),
            File(getExternalFilesDir(null), "custom_diarization/custom_extracted_speaker.wav")
        )
        
        val availableFiles = possibleLocations.filter { it.exists() }
        
        if (availableFiles.isEmpty()) {
            Toast.makeText(this, "No audio files found. Process audio in main app first.", Toast.LENGTH_LONG).show()
            return
        }
        
        // For simplicity, select the first available file
        // In a production app, you might show a file picker
        selectedAudioFile = availableFiles.first()
        btnSelectAudio.text = "Selected: ${selectedAudioFile!!.name}"
        updateStatus("🎵 Audio file selected: ${selectedAudioFile!!.name} (${selectedAudioFile!!.length() / 1024} KB)")
        
        if (sentimentAnalyzer.isInitialized()) {
            btnAnalyzeAudio.isEnabled = true
        }
    }
    
    private fun analyzeSelectedAudio() {
        val audioFile = selectedAudioFile ?: return
        
        btnAnalyzeAudio.isEnabled = false
        progressBar.visibility = View.VISIBLE
        updateStatus("🧠 Analyzing audio sentiment...")
        resultText.text = ""
        
        lifecycleScope.launch {
            sentimentAnalyzer.analyzeAudioSentiment(audioFile.absolutePath).fold(
                onSuccess = { result ->
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        btnAnalyzeAudio.isEnabled = true
                        displaySentimentResult(result)
                    }
                },
                onFailure = { error ->
                    val errorMessage = if (error is AudioSentimentError) {
                        "❌ Analysis failed (code ${error.errorCode}): ${error.message}"
                    } else {
                        "❌ Analysis failed: ${error.message}"
                    }
                    updateStatus(errorMessage)
                    resultText.text = errorMessage
                    Log.e(TAG, "Audio analysis failed", error)
                }
            )
        }
    }
    
    private fun displaySentimentResult(result: AudioSentimentResult) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(result.timestamp))
        
        val resultDisplay = """
            🎭 EMOTIONAL ANALYSIS RESULTS
            ═══════════════════════════════
            
            📁 Audio File: ${result.audioFileName}
            📊 File Size: ${result.audioFileSize / 1024} KB
            ⏰ Analyzed: $timestamp
            
            🎯 PRIMARY EMOTION: ${result.primaryEmotion}
            🎪 Secondary Emotions: ${result.secondaryEmotions}
            
            📈 INTENSITY: ${result.intensity}/10
            💭 VALENCE: ${result.valence}
            ⚡ AROUSAL: ${result.arousal}
            
            📝 ANALYSIS:
            ${result.explanation}
            
            ═══════════════════════════════
            🤖 Analysis powered by Gemma3n
        """.trimIndent()
        
        resultText.text = resultDisplay
        updateStatus("✅ Sentiment analysis completed!")
        
        Log.i(TAG, "Sentiment analysis result: $result")
    }
    
    private fun updateStatus(message: String) {
        statusText.text = message
        Log.i(TAG, message)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sentimentAnalyzer.cleanup()
    }
    
    companion object {
        /**
         * Helper method to start SentimentActivity with an audio file
         */
        fun startWithAudioFile(context: android.content.Context, audioFilePath: String) {
            val intent = Intent(context, SentimentActivity::class.java)
            intent.putExtra("audio_file_path", audioFilePath)
            context.startActivity(intent)
        }
    }
} 


