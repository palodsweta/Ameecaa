package com.ameekaa.app.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ameekaa.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.sentimentDataStore: DataStore<Preferences> by preferencesDataStore(name = "sentiment_analysis")

class SentimentAnalysisDataStore(private val context: Context) {
    
    companion object {
        private val SENTIMENT_RESULTS_KEY = stringPreferencesKey("sentiment_results")
        private const val TAG = "SentimentAnalysisDataStore"
        
        private val initialSentimentResults = listOf(
            // Cheta - User 1001
            SentimentAnalysisResult(
                id = 1L,
                userId = "1001",
                dateTime = "2025-01-26T13:05:01",
                audioFileName = null,
                audioFilePath = null,
                audioDurationSeconds = null,
                primaryEmotion = "anxiety",
                sentiment = SentimentType.NEGATIVE,
                confidenceScore = 0.85f,
                isNegativeSpiralDetected = true,
                detectedTriggers = listOf("self-doubt", "feeling overwhelmed", "difficulty coping"),
                reasoning = "The user expresses difficulty understanding their own thoughts and feelings, and mentions feeling like their mind is 'doing things to their brain'. This suggests a potential negative thought pattern where they are struggling to manage their internal experience.",
                keyThemes = listOf("self-reflection", "emotional processing", "internal struggle"),
                importantKeywords = listOf("mind", "thoughts", "feeling", "difficult", "understand"),
                processingMethod = ProcessingMethod.MULTIMODAL_AUDIO,
                modelVersion = "gemma-3n-E2B-it-int4",
                processingTimeMs = null,
                emotionalIntensity = EmotionalIntensity.HIGH,
                valence = Valence.NEGATIVE,
                arousal = Arousal.HIGH,
                notes = null
            ),
            // Deepika - User 1002
            SentimentAnalysisResult(
                id = 2L,
                userId = "1002",
                dateTime = "2025-01-26T14:22:15",
                audioFileName = null,
                audioFilePath = null,
                audioDurationSeconds = null,
                primaryEmotion = "sadness",
                sentiment = SentimentType.NEGATIVE,
                confidenceScore = 0.92f,
                isNegativeSpiralDetected = true,
                detectedTriggers = listOf("low self-worth", "lack of purpose", "feeling of being overwhelmed"),
                reasoning = "The speaker repeatedly expresses feelings of emptiness and a lack of motivation, suggesting a potential downward spiral of negative self-perception and emotional exhaustion.",
                keyThemes = listOf("emotional distress", "lack of motivation", "sense of emptiness", "personal struggles"),
                importantKeywords = listOf("feeling", "empty", "tired", "sad", "purpose"),
                processingMethod = ProcessingMethod.MULTIMODAL_AUDIO,
                modelVersion = "gemma-3n-E2B-it-int4",
                processingTimeMs = null,
                emotionalIntensity = EmotionalIntensity.VERY_HIGH,
                valence = Valence.VERY_NEGATIVE,
                arousal = Arousal.HIGH,
                notes = null
            ),
            // Raj - User 1003
            SentimentAnalysisResult(
                id = 3L,
                userId = "1003",
                dateTime = "2025-01-26T16:45:33",
                audioFileName = null,
                audioFilePath = null,
                audioDurationSeconds = null,
                primaryEmotion = "anxiety",
                sentiment = SentimentType.NEGATIVE,
                confidenceScore = 0.92f,
                isNegativeSpiralDetected = true,
                detectedTriggers = listOf("fear of heart attack", "panic", "worry about health"),
                reasoning = "The speaker expresses intense fear and physical symptoms (chest pain, shortness of breath) related to a potential cardiac event. This suggests a negative thought spiral centered around a perceived threat to their well-being.",
                keyThemes = listOf("health anxiety", "physical symptoms", "fear", "panic"),
                importantKeywords = listOf("heart", "chest pain", "breath", "fear", "panic"),
                processingMethod = ProcessingMethod.MULTIMODAL_AUDIO,
                modelVersion = "gemma-3n-E2B-it-int4",
                processingTimeMs = null,
                emotionalIntensity = EmotionalIntensity.VERY_HIGH,
                valence = Valence.NEGATIVE,
                arousal = Arousal.HIGH,
                notes = null
            ),
            // Sami - User 1004
            SentimentAnalysisResult(
                id = 4L,
                userId = "1004",
                dateTime = "2025-01-26T18:12:47",
                audioFileName = null,
                audioFilePath = null,
                audioDurationSeconds = null,
                primaryEmotion = "sadness",
                sentiment = SentimentType.NEGATIVE,
                confidenceScore = 0.90f,
                isNegativeSpiralDetected = true,
                detectedTriggers = listOf("isolation", "lack of understanding", "self-diagnosis", "feeling overwhelmed by emotions"),
                reasoning = "The speaker describes feeling isolated and unable to communicate their distress, leading to a self-diagnosis of depression. This suggests a potential negative spiral where feelings of sadness and lack of support reinforce the perception of a serious mental health issue.",
                keyThemes = listOf("mental health", "emotional distress", "communication difficulties", "self-awareness"),
                importantKeywords = listOf("depression", "sadness", "talk", "understand"),
                processingMethod = ProcessingMethod.MULTIMODAL_AUDIO,
                modelVersion = "gemma-3n-E2B-it-int4",
                processingTimeMs = null,
                emotionalIntensity = EmotionalIntensity.HIGH,
                valence = Valence.NEGATIVE,
                arousal = Arousal.HIGH,
                notes = null
            )
        )
    }
    
    suspend fun initializeDefaultSentimentData() {
        try {
            // Check if data already exists
            val existingData = sentimentResults.firstOrNull()
            if (existingData?.isNotEmpty() == true) {
                return // Data already initialized
            }
            
            // Initialize with default data
            context.sentimentDataStore.edit { preferences ->
                val json = Json.encodeToString(initialSentimentResults)
                preferences[SENTIMENT_RESULTS_KEY] = json
            }
        } catch (e: Exception) {
            // Log error but don't crash the app
            android.util.Log.e(TAG, "Error initializing default sentiment data: ${e.message}", e)
        }
    }
    
    /**
     * Get all sentiment analysis results as a Flow
     */
    val sentimentResults: Flow<List<SentimentAnalysisResult>> = context.sentimentDataStore.data
        .map { preferences ->
            val json = preferences[SENTIMENT_RESULTS_KEY] ?: return@map emptyList<SentimentAnalysisResult>()
            try {
                Json.decodeFromString<List<SentimentAnalysisResult>>(json)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error decoding sentiment results: ${e.message}", e)
                emptyList()
            }
        }
    
    /**
     * Add a new sentiment analysis result
     */
    suspend fun addSentimentResult(result: SentimentAnalysisResult) {
        try {
            context.sentimentDataStore.edit { preferences ->
                val currentResults = preferences[SENTIMENT_RESULTS_KEY]?.let {
                    Json.decodeFromString<List<SentimentAnalysisResult>>(it)
                } ?: emptyList()
                
                val updatedResults = currentResults + result
                preferences[SENTIMENT_RESULTS_KEY] = Json.encodeToString(updatedResults)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error adding sentiment result: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get sentiment results for a specific user
     */
    suspend fun getSentimentResultsForUser(userId: String): List<SentimentAnalysisResult> {
        return try {
            sentimentResults.firstOrNull()?.filter { it.userId == userId } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting user sentiment results: ${e.message}", e)
            emptyList()
        }
    }
    
         /**
      * Get recent sentiment results (last N days)
      */
     suspend fun getRecentSentimentResults(days: Int = 7): List<SentimentAnalysisResult> {
         return try {
             // For simplicity, return all results when dealing with API compatibility
             // In a real implementation, you'd use a proper date parsing library
             sentimentResults.firstOrNull() ?: emptyList()
         } catch (e: Exception) {
             android.util.Log.e(TAG, "Error getting recent sentiment results: ${e.message}", e)
             emptyList()
         }
     }
    
    /**
     * Get sentiment results with negative spiral detection
     */
    suspend fun getNegativeSpiralResults(): List<SentimentAnalysisResult> {
        return try {
            sentimentResults.firstOrNull()?.filter { it.isNegativeSpiralDetected } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting negative spiral results: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Update an existing sentiment result
     */
    suspend fun updateSentimentResult(resultId: Long, updatedResult: SentimentAnalysisResult) {
        try {
            context.sentimentDataStore.edit { preferences ->
                val currentResults = preferences[SENTIMENT_RESULTS_KEY]?.let {
                    Json.decodeFromString<List<SentimentAnalysisResult>>(it)
                } ?: emptyList()
                
                val updatedResults = currentResults.map { 
                    if (it.id == resultId) updatedResult else it 
                }
                preferences[SENTIMENT_RESULTS_KEY] = Json.encodeToString(updatedResults)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating sentiment result: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Delete a sentiment result
     */
    suspend fun deleteSentimentResult(resultId: Long) {
        try {
            context.sentimentDataStore.edit { preferences ->
                val currentResults = preferences[SENTIMENT_RESULTS_KEY]?.let {
                    Json.decodeFromString<List<SentimentAnalysisResult>>(it)
                } ?: emptyList()
                
                val updatedResults = currentResults.filter { it.id != resultId }
                preferences[SENTIMENT_RESULTS_KEY] = Json.encodeToString(updatedResults)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error deleting sentiment result: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Delete all sentiment results for a user
     */
    suspend fun deleteSentimentResultsForUser(userId: String) {
        try {
            context.sentimentDataStore.edit { preferences ->
                val currentResults = preferences[SENTIMENT_RESULTS_KEY]?.let {
                    Json.decodeFromString<List<SentimentAnalysisResult>>(it)
                } ?: emptyList()
                
                val updatedResults = currentResults.filter { it.userId != userId }
                preferences[SENTIMENT_RESULTS_KEY] = Json.encodeToString(updatedResults)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error deleting user sentiment results: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Clear all sentiment analysis data
     */
    suspend fun clearAllSentimentData() {
        try {
            context.sentimentDataStore.edit { preferences ->
                preferences.remove(SENTIMENT_RESULTS_KEY)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error clearing sentiment data: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get sentiment analysis statistics for a user
     */
    suspend fun getSentimentStatsForUser(userId: String, days: Int = 30): SentimentAnalysisStats {
        return try {
                         val userResults = getSentimentResultsForUser(userId)
             // For API compatibility, use all user results instead of filtering by date
             val recentResults = userResults
            
            if (recentResults.isEmpty()) {
                return SentimentAnalysisStats(
                    totalAnalyses = 0,
                    negativeSpiralCount = 0,
                    averageConfidence = 0f,
                    mostCommonEmotion = "None",
                    mostCommonTriggers = emptyList(),
                    trendDirection = TrendDirection.UNKNOWN,
                    timeRange = "$days days"
                )
            }
            
            val negativeSpiralCount = recentResults.count { it.isNegativeSpiralDetected }
            val averageConfidence = recentResults.map { it.confidenceScore }.average().toFloat()
            val mostCommonEmotion = recentResults.groupBy { it.primaryEmotion }
                .maxByOrNull { it.value.size }?.key ?: "Unknown"
            
            val allTriggers = recentResults.flatMap { it.detectedTriggers }
            val mostCommonTriggers = allTriggers.groupBy { it }
                .entries.sortedByDescending { it.value.size }
                .take(5)
                .map { it.key }
            
            // Simple trend calculation based on recent vs older results
            val trendDirection = calculateTrendDirection(recentResults)
            
            SentimentAnalysisStats(
                totalAnalyses = recentResults.size,
                negativeSpiralCount = negativeSpiralCount,
                averageConfidence = averageConfidence,
                mostCommonEmotion = mostCommonEmotion,
                mostCommonTriggers = mostCommonTriggers,
                trendDirection = trendDirection,
                timeRange = "$days days"
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error calculating sentiment stats: ${e.message}", e)
            SentimentAnalysisStats(
                totalAnalyses = 0,
                negativeSpiralCount = 0,
                averageConfidence = 0f,
                mostCommonEmotion = "Error",
                mostCommonTriggers = emptyList(),
                trendDirection = TrendDirection.UNKNOWN,
                timeRange = "$days days"
            )
        }
    }
    
    /**
     * Calculate trend direction based on sentiment results
     */
    private fun calculateTrendDirection(results: List<SentimentAnalysisResult>): TrendDirection {
        if (results.size < 2) return TrendDirection.UNKNOWN
        
        val sortedResults = results.sortedBy { it.dateTime }
        val firstHalf = sortedResults.take(sortedResults.size / 2)
        val secondHalf = sortedResults.drop(sortedResults.size / 2)
        
        val firstHalfNegativeRatio = firstHalf.count { it.sentiment == SentimentType.NEGATIVE }.toFloat() / firstHalf.size
        val secondHalfNegativeRatio = secondHalf.count { it.sentiment == SentimentType.NEGATIVE }.toFloat() / secondHalf.size
        
        return when {
            secondHalfNegativeRatio < firstHalfNegativeRatio - 0.1f -> TrendDirection.IMPROVING
            secondHalfNegativeRatio > firstHalfNegativeRatio + 0.1f -> TrendDirection.DECLINING
            else -> TrendDirection.STABLE
        }
    }
    
    /**
     * Export sentiment data for backup or analysis (returns JSON string)
     */
    suspend fun exportSentimentData(): String {
        return try {
            val allResults = sentimentResults.firstOrNull() ?: emptyList()
            Json.encodeToString(allResults)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error exporting sentiment data: ${e.message}", e)
            "[]"
        }
    }
    
         /**
      * Import sentiment data from backup (accepts JSON string)
      */
     suspend fun importSentimentData(jsonData: String, replaceExisting: Boolean = false) {
         try {
             val importedResults = Json.decodeFromString<List<SentimentAnalysisResult>>(jsonData)
             
             context.sentimentDataStore.edit { preferences ->
                 val currentResults = if (replaceExisting) {
                     emptyList()
                 } else {
                     preferences[SENTIMENT_RESULTS_KEY]?.let {
                         Json.decodeFromString<List<SentimentAnalysisResult>>(it)
                     } ?: emptyList()
                 }
                 
                 val mergedResults = currentResults + importedResults
                 preferences[SENTIMENT_RESULTS_KEY] = Json.encodeToString(mergedResults)
             }
         } catch (e: Exception) {
             android.util.Log.e(TAG, "Error importing sentiment data: ${e.message}", e)
             throw e
         }
     }
     
     /**
      * Convenience method to save a new sentiment result
      */
     suspend fun saveSentimentResult(
         userId: String,
         audioFile: java.io.File?,
         primaryEmotion: String,
         sentiment: SentimentType,
         confidenceScore: Float,
         isNegativeSpiralDetected: Boolean,
         detectedTriggers: List<String>,
         reasoning: String,
         keyThemes: List<String>,
         importantKeywords: List<String>,
         processingMethod: ProcessingMethod = ProcessingMethod.MULTIMODAL_AUDIO,
         modelVersion: String = "gemma-3n-E2B-it-int4",
         processingTimeMs: Long? = null,
         emotionalIntensity: EmotionalIntensity? = null,
         valence: Valence? = null,
         arousal: Arousal? = null,
         notes: String? = null
     ): SentimentAnalysisResult {
         
         val result = SentimentAnalysisResult(
             id = SentimentAnalysisResult.generateId(),
             userId = userId,
             dateTime = SentimentAnalysisResult.getCurrentDateTime(),
             audioFileName = audioFile?.name,
             audioFilePath = audioFile?.absolutePath,
             audioDurationSeconds = calculateAudioDuration(audioFile),
             primaryEmotion = primaryEmotion,
             sentiment = sentiment,
             confidenceScore = confidenceScore,
             isNegativeSpiralDetected = isNegativeSpiralDetected,
             detectedTriggers = detectedTriggers,
             reasoning = reasoning,
             keyThemes = keyThemes,
             importantKeywords = importantKeywords,
             processingMethod = processingMethod,
             modelVersion = modelVersion,
             processingTimeMs = processingTimeMs,
             emotionalIntensity = emotionalIntensity,
             valence = valence,
             arousal = arousal,
             notes = notes
         )
         
         addSentimentResult(result)
         return result
     }
     
     /**
      * Calculate audio duration from file (basic estimation)
      */
     private fun calculateAudioDuration(audioFile: java.io.File?): Float? {
         return try {
             audioFile?.let {
                 // Basic estimation for WAV files: file size / (sample rate * channels * bytes per sample)
                 val fileSizeBytes = it.length()
                 val estimatedDurationSeconds = fileSizeBytes / (16000 * 1 * 2) // 16kHz, mono, 16-bit
                 estimatedDurationSeconds.toFloat()
             }
         } catch (e: Exception) {
             android.util.Log.w(TAG, "Could not calculate audio duration: ${e.message}")
             null
         }
     }
} 


