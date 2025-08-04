package com.ameekaa.app.sentiment

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioSentimentAnalyzer(
    private val context: Context
) {
    companion object {
        private const val TAG = "AudioSentimentAnalyzer"
        private const val MODEL_FILENAME = "gemma-3n-E2B-it-int4.task"
    }

    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private val modelFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), MODEL_FILENAME)

    fun isModelInitialized(): Boolean {
        return llmInference != null && llmSession != null
    }

    suspend fun initializeModel(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isModelInitialized()) {
                Log.i(TAG, "✅ Model already initialized")
                return@withContext true
            }

            if (!modelFile.exists()) {
                Log.e(TAG, "❌ Model file not found at: ${modelFile.absolutePath}")
                return@withContext false
            }

            // Initialize LLM engine
            val llmOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, llmOptions)

            // Create session
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.95f)
                .setTemperature(0.7f)
                .build()

            llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            Log.i(TAG, "✅ Model initialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing model: ${e.message}", e)
            cleanup()
            false
        }
    }

    suspend fun sentimentAnalysis(audioFile: File): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!isModelInitialized()) {
                Log.e(TAG, "❌ Model not initialized")
                return@withContext null
            }

            if (!audioFile.exists()) {
                Log.e(TAG, "❌ Audio file not found: ${audioFile.path}")
                return@withContext null
            }

            val prompt = """
                Analyze this audio for emotional content and sentiment.
                Audio file: ${audioFile.name}
                Provide analysis in JSON format with:
                - Primary emotion
                - Sentiment (positive/negative/neutral)
                - Confidence score
                - Detected triggers
                - Key themes
            """.trimIndent()

            llmSession!!.addQueryChunk(prompt)
            llmSession!!.generateResponse()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in sentiment analysis: ${e.message}", e)
            null
        }
    }

    suspend fun nudgeGeneration(prompt: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!isModelInitialized()) {
                Log.e(TAG, "❌ Model not initialized")
                return@withContext null
            }

            // Log and add prompt
            Log.i(TAG, "📝 Prompt for nudge generation:")
            Log.i(TAG, "=== PROMPT START ===")
            Log.i(TAG, prompt)
            Log.i(TAG, "=== PROMPT END ===")
            
            // Generate response
            llmSession!!.addQueryChunk(prompt)
            val response = llmSession!!.generateResponse()
            
            // Log response
            Log.i(TAG, "✅ Gemma response:")
            Log.i(TAG, "=== RESPONSE START ===")
            Log.i(TAG, "${response ?: "NULL"}")
            Log.i(TAG, "=== RESPONSE END ===")
            response

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in nudge generation: ${e.message}", e)
            null
        }
    }

    private fun cleanup() {
        try {
            llmSession?.close()
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Error during cleanup: ${e.message}")
        } finally {
            llmSession = null
            llmInference = null
        }
    }
} 


