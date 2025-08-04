package com.ameekaa.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.media.MediaPlayer
import com.ameekaa.app.sentiment.SentimentActivity
import androidx.appcompat.widget.Toolbar
import androidx.activity.OnBackPressedCallback

class DiarizationTest : AppCompatActivity() {
    companion object {
        private const val TAG = "DiarizationTest"
    }

    // UI Components
    private lateinit var btnSelectEnrollment: Button
    private lateinit var btnSelectMeeting: Button
    private lateinit var btnSubmit: Button
    private lateinit var btnPlayOutput: Button
    private lateinit var btnSentimentAnalysis: Button
    private lateinit var enrollmentFileName: TextView
    private lateinit var meetingFileName: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    // File selection
    private var enrollmentUri: Uri? = null
    private var meetingUri: Uri? = null
    private var outputFileUri: Uri? = null
    
    // Media player for built-in audio playback
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    // Initialize diarization engine lazily (will be recreated for each test)
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

    private val enrollmentPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                enrollmentUri = uri
                enrollmentFileName.text = getFileName(uri)
                updateSubmitButtonState()
            }
        }
    }

    private val meetingPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                meetingUri = uri
                meetingFileName.text = getFileName(uri)
                updateSubmitButtonState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mvp_test)

        // Set up toolbar with back button
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Diarization Test"
        }

        // Initialize views lazily to prevent ANR
        lifecycleScope.launch {
            initializeViews()
            setupClickListeners()
        }
        setupBackHandling()
    }

    private fun initializeViews() {
        btnSelectEnrollment = findViewById(R.id.btnSelectEnrollment)
        btnSelectMeeting = findViewById(R.id.btnSelectMeeting)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnPlayOutput = findViewById(R.id.btnPlayOutput)
        btnSentimentAnalysis = findViewById(R.id.btnSentimentAnalysis)
        enrollmentFileName = findViewById(R.id.enrollmentFileName)
        meetingFileName = findViewById(R.id.meetingFileName)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        // Set initial state
        enrollmentFileName.text = "No file selected"
        meetingFileName.text = "No file selected"
        btnSubmit.isEnabled = false
        btnPlayOutput.visibility = View.GONE
        btnSentimentAnalysis.visibility = View.GONE
    }

    private fun setupClickListeners() {
        btnSelectEnrollment.setOnClickListener {
            openAudioPicker(enrollmentPicker)
        }

        btnSelectMeeting.setOnClickListener {
            openAudioPicker(meetingPicker)
        }

        btnSubmit.setOnClickListener {
            processCustomAudioFiles()
        }

        btnPlayOutput.setOnClickListener {
            playOutputAudio()
        }

        btnSentimentAnalysis.setOnClickListener {
            openSentimentAnalysis()
        }
    }
    
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

    private fun openAudioPicker(picker: androidx.activity.result.ActivityResultLauncher<Intent>) {
        // Try multiple approaches to ensure local storage access
        val intents = mutableListOf<Intent>()
        
        // Primary intent - Documents UI with storage access
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
            // Force local storage access
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            putExtra("android.content.extra.FANCY", true)
            putExtra("android.content.extra.LOCAL_ONLY", true)
        }
        intents.add(primaryIntent)
        
        // Fallback intent - GET_CONTENT for broader compatibility
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
        
        // Create chooser with multiple options
        val chooserIntent = Intent.createChooser(primaryIntent, "Select Audio File")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(fallbackIntent))
        
        try {
            Log.i(TAG, "Opening file picker with local storage preference")
            picker.launch(chooserIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Chooser failed, trying fallback: ${e.message}")
            try {
                picker.launch(primaryIntent)
            } catch (e2: Exception) {
                Log.w(TAG, "Primary intent failed, trying simple GET_CONTENT: ${e2.message}")
                picker.launch(fallbackIntent)
            }
        }
    }

    private fun updateSubmitButtonState() {
        btnSubmit.isEnabled = enrollmentUri != null && meetingUri != null
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: "Unknown file"
    }

    private fun copyUriToFile(uri: Uri, file: File) {
        try {
            // Validate file is accessible
            val mimeType = contentResolver.getType(uri)
            Log.i(TAG, "Copying file with MIME type: $mimeType")
            
            // Ensure parent directory exists
            file.parentFile?.mkdirs()
            
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    val bytesWritten = input.copyTo(output)
                    Log.i(TAG, "Successfully copied ${bytesWritten} bytes to ${file.absolutePath}")
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $uri")
            
            // Validate the copied file
            if (!file.exists() || file.length() == 0L) {
                throw IllegalStateException("File copy failed or resulted in empty file")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file from URI $uri to ${file.absolutePath}: ${e.message}", e)
            throw e
        }
    }

    private fun playOutputAudio() {
        if (isPlaying) {
            // Stop playback
            stopAudioPlayback()
        } else {
            // Start playback
            startAudioPlayback()
        }
    }
    
    private fun startAudioPlayback() {
        outputFileUri?.let { uri ->
            try {
                // Release any existing MediaPlayer
                stopAudioPlayback()
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@DiarizationTest, uri)
                    setOnPreparedListener { mp ->
                        mp.start()
                        runOnUiThread {
                            this@DiarizationTest.isPlaying = true
                            btnPlayOutput.text = "⏸️ Stop Audio"
                            val duration = mp.duration / 1000
                            statusText.text = "🎵 Playing audio (${duration}s)..."
                        }
                        Log.i(TAG, "Started audio playback")
                    }
                    setOnCompletionListener {
                        stopAudioPlayback()
                        runOnUiThread {
                            statusText.text = "✅ Audio playback completed"
                        }
                        Log.i(TAG, "Audio playback completed")
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        runOnUiThread {
                            statusText.text = "❌ Audio playback error"
                            resetPlayButton()
                        }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting audio playback: ${e.message}", e)
                runOnUiThread {
                    statusText.text = "❌ Error playing audio: ${e.message}"
                    resetPlayButton()
                }
            }
        } ?: run {
            Log.w(TAG, "No output file URI available")
            runOnUiThread {
                statusText.text = "⚠️ No audio file available to play"
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
                Log.w(TAG, "Error stopping MediaPlayer: ${e.message}")
            }
        }
        mediaPlayer = null
        isPlaying = false
        runOnUiThread {
            resetPlayButton()
        }
    }
    
    private fun resetPlayButton() {
        btnPlayOutput.text = "▶️ Play Audio"
    }

    private fun openSentimentAnalysis() {
        val currentOutputFile = outputFileUri?.let { uri ->
            // Try to get the actual file path from the URI
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, "user_custom_segments.wav")
        } ?: run {
            // Check for other possible locations
            val possibleFiles = listOf(
                File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "user_custom_segments.wav"),
                File(filesDir, "user_custom_segments.wav"),
                File(getExternalFilesDir(null), "custom_diarization/custom_extracted_speaker.wav")
            )
            possibleFiles.firstOrNull { it.exists() }
        }

        if (currentOutputFile?.exists() == true) {
            SentimentActivity.startWithAudioFile(this, currentOutputFile.absolutePath)
        } else {
            Toast.makeText(this, "No audio file available for sentiment analysis", Toast.LENGTH_SHORT).show()
            // Open sentiment analysis activity without a file
            val intent = Intent(this, SentimentActivity::class.java)
            startActivity(intent)
        }
    }

    private fun processCustomAudioFiles() {
        if (enrollmentUri == null || meetingUri == null) {
            Toast.makeText(this, "Please select both enrollment and meeting audio files", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress and hide previous results
        progressBar.visibility = View.VISIBLE
        btnSubmit.isEnabled = false
        outputFileUri = null
        statusText.text = "Processing custom audio files..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val enrollmentPath = copyUriToInternalStorage(enrollmentUri!!, "enrollment.wav")
                val meetingPath = copyUriToInternalStorage(meetingUri!!, "meeting.wav")
                val modelPath = copyModelToInternalStorage()

                updateStatus("Custom files ready")
                updateStatus("Starting speaker diarization...")

                val diarizationEngine = createDiarizationEngine(modelPath)
                
                updateStatus("Step 1/3: Processing enrollment audio...")

                val enrollmentEmbedding = enrollSpeaker(
                    enrollmentPath = enrollmentPath,
                    speakerName = "user_custom",
                    modelPath = modelPath,
                    sampleRate = 16000,
                    context = this@DiarizationTest
                )
                
                updateStatus("Step 2/3: Processing meeting audio...")

                val segments = diarizationEngine.diarizeMeeting(
                    meetingPath = meetingPath,
                    enrollmentEmbedding = enrollmentEmbedding,
                    speakerName = "user_custom",
                    threshold = 0.6f 
                )
                
                updateStatus("Step 3/3: Extracting speaker segments...")

                if (segments.isNotEmpty()) {
                    val outputPath = filesDir.absolutePath + "/user_custom_segments.wav"
                    diarizationEngine.extractSegments(
                        meetingPath = meetingPath,
                        segments = segments,
                        outputPath = outputPath
                    )
                    
                    val totalDuration = segments.sumOf { it.endTime - it.startTime }
                    val outputFile = File(outputPath)
                    
                    updateStatus("✅ Processing completed!")
                    updateStatus("Found ${segments.size} speaker segments")
                    updateStatus("Total duration: ${String.format("%.1f", totalDuration)}s")
                    
                    try {
                        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        downloadsDir?.mkdirs()
                        val publicOutputFile = File(downloadsDir, "user_custom_segments.wav")
                        outputFile.copyTo(publicOutputFile, overwrite = true)
                        updateStatus("📁 Output saved to: ${publicOutputFile.name}")
                        Log.i(TAG, "Output file accessible at: ${publicOutputFile.absolutePath}")
                        
                        outputFileUri = FileProvider.getUriForFile(
                            this@DiarizationTest,
                            "${packageName}.fileprovider",
                            publicOutputFile
                        )
                        
                        withContext(Dispatchers.Main) {
                            btnPlayOutput.visibility = View.VISIBLE
                            btnPlayOutput.isEnabled = true
                            btnPlayOutput.text = "▶️ Play Custom Audio"
                            btnSentimentAnalysis.visibility = View.VISIBLE
                            btnSentimentAnalysis.isEnabled = true
                        }
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not copy to public directory: ${e.message}")
                        updateStatus("⚠️ File created in app internal storage")
                        outputFileUri = FileProvider.getUriForFile(
                            this@DiarizationTest,
                            "${packageName}.fileprovider",
                            outputFile
                        )
                        withContext(Dispatchers.Main) {
                            btnPlayOutput.visibility = View.VISIBLE
                            btnPlayOutput.isEnabled = true
                            btnSentimentAnalysis.visibility = View.VISIBLE
                            btnSentimentAnalysis.isEnabled = true
                        }
                    }
                } else {
                    updateStatus("⚠️ No matching speaker segments found")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing custom files: ${e.message}", e)
                updateStatus("❌ Error: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnSubmit.isEnabled = true
                }
            }
        }
    }
    
    private suspend fun updateStatus(message: String) {
        withContext(Dispatchers.Main) {
            statusText.text = message
            Log.i(TAG, message)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up MediaPlayer resources
        stopAudioPlayback()
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 


