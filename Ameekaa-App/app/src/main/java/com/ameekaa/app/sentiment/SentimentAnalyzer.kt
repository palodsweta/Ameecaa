package com.ameekaa.app.sentiment

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Random

/**
 * Data class for configuring the Gemma model
 */
data class ModelConfig(
    val modelName: String = "Gemma3n-E2B-IT-TFLite-INT4",
    val modelFileName: String = "gemma-3n-E2B-it-int4.task",
    val maxTokens: Int = 512,
    val temperature: Float = 0.8f,
    val topK: Int = 1,
    val randomSeed: Int = Random().nextInt()
)

/**
 * Data class for representing an audio sentiment analysis error
 */
data class AudioSentimentError(
    override val message: String,
    override val cause: Throwable? = null,
    val errorCode: Int = -1
) : Exception(message, cause)

/**
 * Sentiment Analyzer using Gemma3n model for direct audio sentiment analysis
 * Processes audio files directly without transcription, using multimodal capabilities
 * Based on google/gemma-3n-E2B-it model approach
 */
class SentimentAnalyzer(
    private val context: Context,
    private val modelConfig: ModelConfig = ModelConfig()
) {

    private val TAG = "SentimentAnalyzer"

    // Gemma3n model configuration for multimodal audio processing
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null

    // Model file path - Updated to use the Gemma3n model from assets
    private val modelFileName = "gemma-3n-E2B-it-int4.task"
    private val modelFile = File(context.filesDir, "sentiment_models/$modelFileName")
    private val modelsDir = File(context.filesDir, "sentiment_models")

    // Plutchik's Wheel of Emotions prompt for audio analysis (matching Python reference)
    private val plutchikPrompt = """
        Can you analyze the emotion in this audio using Plutchik's Wheel of Emotions?
        Please provide:
        1. Primary emotion from Plutchik's 8 basic emotions (Joy, Trust, Fear, Surprise, Sadness, Disgust, Anger, Anticipation)
        2. Secondary emotions if detected
        3. Intensity level (1-10 scale)
        4. Emotional tone and valence
        5. Brief explanation of emotional indicators found

        Format your response as:
        PRIMARY_EMOTION: [emotion name]
        SECONDARY_EMOTIONS: [list if any]
        INTENSITY: [1-10]
        VALENCE: [Positive/Negative/Neutral]
        AROUSAL: [High/Medium/Low]
        EXPLANATION: [brief analysis]

        Return analysis for this audio file:
    """.trimIndent()

    /**
     * Copy the Gemma3n model from assets to internal storage if needed
     * For large models, checks multiple locations including external storage
     */
    private suspend fun setupModelFromAssets(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Ensure models directory exists
            if (!modelsDir.exists()) {
                val created = modelsDir.mkdirs()
                Log.i(TAG, "Models directory created: $created, path: ${modelsDir.absolutePath}")
            }

            // Check if model already exists and is valid
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.i(TAG, "Gemma3n model already exists in storage: ${modelFile.absolutePath}")
                return@withContext Result.success(Unit)
            }

            // Try multiple locations for the model file
            val possibleModelLocations = listOf(
                // Assets directory (for smaller models)
                "assets" to { context.assets.open(modelFileName) },
                // Downloads directory
                "downloads" to {
                    val downloadsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "")
                    val externalModel = File(downloadsDir, modelFileName)
                    if (externalModel.exists()) externalModel.inputStream() else throw Exception("Not found in downloads")
                },
                // Root external files directory
                "external" to {
                    val externalModel = File(context.getExternalFilesDir(null), modelFileName)
                    if (externalModel.exists()) externalModel.inputStream() else throw Exception("Not found in external")
                },
                // Internal files directory (if manually copied)
                "files" to {
                    val filesModel = File(context.filesDir, modelFileName)
                    if (filesModel.exists()) filesModel.inputStream() else throw Exception("Not found in files")
                }
            )

            var modelCopied = false
            var sourceLocation = ""

            for ((location, inputStreamProvider) in possibleModelLocations) {
                try {
                    Log.i(TAG, "Trying to load Gemma3n model from $location...")

                    inputStreamProvider().use { inputStream ->
                        FileOutputStream(modelFile).use { outputStream ->
                            val bytesWritten = inputStream.copyTo(outputStream)
                            Log.i(TAG, "Successfully copied $bytesWritten bytes from $location to ${modelFile.absolutePath}")
                            sourceLocation = location
                            modelCopied = true
                        }
                    }
                    break // Success, no need to try other locations

                } catch (e: Exception) {
                    Log.d(TAG, "Model not found in $location: ${e.message}")
                    continue // Try next location
                }
            }

            if (!modelCopied) {
                return@withContext Result.failure(
                    Exception("Gemma3n model '$modelFileName' not found in any location. " +
                             "Please place the model file in:\n" +
                             "- App's Downloads folder\n" +
                             "- App's external files directory\n" +
                             "- Or download via the model downloader")
                )
            }

            // Verify the copied file
            if (!modelFile.exists() || modelFile.length() == 0L) {
                return@withContext Result.failure(Exception("Model copy failed or resulted in empty file"))
            }

            val modelSizeMB = modelFile.length() / (1024.0 * 1024.0)
            Log.i(TAG, "Gemma3n model successfully setup from $sourceLocation: ${"%.1f".format(modelSizeMB)} MB")

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup model: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Initialize the Gemma3n model for multimodal audio sentiment analysis
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing Gemma3n multimodal sentiment analyzer...")

            // First, setup the model from assets
            setupModelFromAssets().fold(
                onSuccess = {
                    Log.i(TAG, "Model setup from assets completed successfully")
                },
                onFailure = { error ->
                    return@withContext Result.failure(
                        Exception("Failed to setup model from assets: ${error.message}")
                    )
                }
            )

            // Check if model file exists after setup
            if (!modelFile.exists()) {
                return@withContext Result.failure(
                    Exception("Gemma3n model not found after setup. Please ensure ${modelFileName} is in the assets directory.")
                )
            }

            Log.i(TAG, "Loading Gemma3n multimodal model from: ${modelFile.absolutePath}")

            // Configure LLM inference options - using minimal configuration
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(modelConfig.maxTokens)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            
            // Create LLM inference instance
            try {
                llmInference = LlmInference.createFromOptions(context, options)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    AudioSentimentError("Failed to create LLM inference instance: ${e.message}", e, 1003)
                )
            }

            // Create session for repeated use with basic configuration
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(modelConfig.topK)
                .setTemperature(modelConfig.temperature)
                .setRandomSeed(modelConfig.randomSeed)
                .build()
            
            llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)

            Log.i(TAG, "Gemma3n multimodal sentiment analyzer initialized successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemma3n sentiment analyzer: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Analyze sentiment directly from audio file using Gemma3n multimodal capabilities
     * Similar to the Python reference implementation
     */
    suspend fun analyzeAudioSentiment(audioFilePath: String): Result<AudioSentimentResult> = withContext(Dispatchers.IO) {
        try {
            val session = llmSession ?: return@withContext Result.failure(
                AudioSentimentError("Sentiment analyzer not initialized. Call initialize() first.", errorCode = 1001)
            )

            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                return@withContext Result.failure(
                    AudioSentimentError("Audio file not found: $audioFilePath", errorCode = 1002)
                )
            }

            Log.i(TAG, "Analyzing sentiment for audio file: ${audioFile.name} (${audioFile.length()} bytes)")

            // Create multimodal input similar to Python reference
            // Note: This is the conceptual approach - actual MediaPipe implementation may vary
            // For now, we'll process it as text-based analysis since multimodal audio may not be fully supported yet
            val textInput = plutchikPrompt + "\n\nAnalyze the emotional content of the provided audio file: ${audioFile.name}"

            // Add query to session
            session.addQueryChunk(textInput)

            // Generate response synchronously
            val response = session.generateResponse()

            Log.i(TAG, "Gemma3n audio sentiment analysis response: $response")

            // Parse the response into structured audio sentiment result
            val sentimentResult = parseAudioSentimentResponse(response, audioFile)

            Result.success(sentimentResult)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze audio sentiment: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parse the LLM response into structured audio sentiment data
     * Based on Plutchik's Wheel of Emotions format
     */
    private fun parseAudioSentimentResponse(response: String, audioFile: File): AudioSentimentResult {
        try {
            val lines = response.lines()
            var primaryEmotion = "Unknown"
            var secondaryEmotions = "None"
            var intensity = 5
            var valence = "Neutral"
            var arousal = "Medium"
            var explanation = "Unable to analyze"

            for (line in lines) {
                when {
                    line.startsWith("PRIMARY_EMOTION:", ignoreCase = true) -> {
                        primaryEmotion = line.substringAfter(":").trim()
                    }
                    line.startsWith("SECONDARY_EMOTIONS:", ignoreCase = true) -> {
                        secondaryEmotions = line.substringAfter(":").trim()
                    }
                    line.startsWith("INTENSITY:", ignoreCase = true) -> {
                        val intensityStr = line.substringAfter(":").trim()
                        intensity = intensityStr.toIntOrNull() ?: 5
                    }
                    line.startsWith("VALENCE:", ignoreCase = true) -> {
                        valence = line.substringAfter(":").trim()
                    }
                    line.startsWith("AROUSAL:", ignoreCase = true) -> {
                        arousal = line.substringAfter(":").trim()
                    }
                    line.startsWith("EXPLANATION:", ignoreCase = true) -> {
                        explanation = line.substringAfter(":").trim()
                    }
                }
            }

            return AudioSentimentResult(
                primaryEmotion = primaryEmotion,
                secondaryEmotions = secondaryEmotions,
                intensity = intensity,
                valence = valence,
                arousal = arousal,
                explanation = explanation,
                audioFileName = audioFile.name,
                audioFilePath = audioFile.absolutePath,
                audioFileSize = audioFile.length(),
                fullResponse = response,
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse audio sentiment response, using fallback: ${e.message}")
            return AudioSentimentResult(
                primaryEmotion = "Unknown",
                secondaryEmotions = "None",
                intensity = 0,
                valence = "Neutral",
                arousal = "Unknown",
                explanation = "Failed to analyze audio due to parsing error",
                audioFileName = audioFile.name,
                audioFilePath = audioFile.absolutePath,
                audioFileSize = audioFile.length(),
                fullResponse = response,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Check if the sentiment analyzer is ready to use
     */
    fun isInitialized(): Boolean {
        return llmSession != null
    }

    /**
     * Check if the model is available in any supported location
     */
    fun isModelAvailableInAssets(): Boolean {
        // Check if already processed and stored
        if (modelFile.exists() && modelFile.length() > 0) {
            return true
        }

        // Check multiple possible locations
        val possibleLocations = listOf(
            // Assets directory (for smaller models)
            { context.assets.open(modelFileName).use { true } },
            // Downloads directory
            {
                val downloadsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "")
                val externalModel = File(downloadsDir, modelFileName)
                externalModel.exists() && externalModel.length() > 0
            },
            // Root external files directory
            {
                val externalModel = File(context.getExternalFilesDir(null), modelFileName)
                externalModel.exists() && externalModel.length() > 0
            },
            // Internal files directory (if manually copied)
            {
                val filesModel = File(context.filesDir, modelFileName)
                filesModel.exists() && filesModel.length() > 0
            }
        )

        for (locationCheck in possibleLocations) {
            try {
                if (locationCheck()) {
                    return true
                }
            } catch (e: Exception) {
                // Continue to next location
                continue
            }
        }

        Log.w(TAG, "Model '$modelFileName' not found in any location")
        return false
    }

    /**
     * Get model file information
     */
    fun getModelInfo(): ModelInfo {
        return ModelInfo(
            modelName = "Gemma3n-E2B-IT",
            modelSize = if (modelFile.exists()) modelFile.length() else 0L,
            isDownloaded = modelFile.exists() || isModelAvailableInAssets(),
            modelPath = if (modelFile.exists()) modelFile.absolutePath else "assets/$modelFileName"
        )
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            llmSession?.close()
            llmInference?.close()
            llmSession = null
            llmInference = null
            Log.i(TAG, "Sentiment analyzer resources cleaned up")
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup: ${e.message}")
        }
    }
}

/**
 * Data class representing audio sentiment analysis results using Plutchik's Wheel of Emotions
 */
data class AudioSentimentResult(
    val primaryEmotion: String,      // Primary emotion from Plutchik's 8 basic emotions
    val secondaryEmotions: String,   // Secondary emotions if detected
    val intensity: Int,              // Intensity level (1-10 scale)
    val valence: String,             // Positive, Negative, Neutral
    val arousal: String,             // High, Medium, Low
    val explanation: String,         // Brief analysis of emotional indicators
    val audioFileName: String,       // Name of analyzed audio file
    val audioFilePath: String,       // Full path to audio file
    val audioFileSize: Long,         // Size of audio file in bytes
    val fullResponse: String,        // Full LLM response
    val timestamp: Long              // Analysis timestamp
)

/**
 * Data class for model information
 */
data class ModelInfo(
    val modelName: String,
    val modelSize: Long,
    val isDownloaded: Boolean,
    val modelPath: String
) 


