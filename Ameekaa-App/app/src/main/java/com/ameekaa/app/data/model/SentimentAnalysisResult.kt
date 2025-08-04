package com.ameekaa.app.data.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data model for sentiment analysis results from audio processing
 * Stores comprehensive emotional analysis including negative spiral detection
 */
@Serializable
data class SentimentAnalysisResult(
    val id: Long,
    val userId: String,
    val dateTime: String, // ISO 8601 format for serialization
    val audioFileName: String? = null,
    val audioFilePath: String? = null,
    val audioDurationSeconds: Float? = null,
    
    // Primary emotion analysis
    val primaryEmotion: String, // e.g., "sadness", "joy", "anger"
    val sentiment: SentimentType,
    val confidenceScore: Float, // 0.0 to 1.0
    
    // Negative spiral detection
    val isNegativeSpiralDetected: Boolean,
    val detectedTriggers: List<String>, // e.g., ["low self-worth", "isolation", "self-blame"]
    val reasoning: String, // Detailed explanation from the model
    
    // Topic and keyword extraction
    val keyThemes: List<String>, // e.g., ["emotional distress", "self-awareness", "seeking understanding"]
    val importantKeywords: List<String>, // e.g., ["depression", "sadness", "isolation"]
    
    // Processing metadata
    val processingMethod: ProcessingMethod = ProcessingMethod.MULTIMODAL_AUDIO,
    val modelVersion: String = "gemma-3n-E2B-it-int4",
    val processingTimeMs: Long? = null,
    
    // Additional context
    val emotionalIntensity: EmotionalIntensity? = null,
    val valence: Valence? = null, // Positive/Negative emotional charge
    val arousal: Arousal? = null, // Energy level of emotion
    val notes: String? = null // User or system notes
) {
    
    companion object {
        fun getCurrentDateTime(): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            return dateFormat.format(Date())
        }
        
        fun generateId(): Long {
            return System.currentTimeMillis()
        }
    }
    
    /**
     * Get formatted date for display
     */
    fun getFormattedDateTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateTime
        }
    }
    
    /**
     * Check if this is a high-risk sentiment requiring intervention
     */
    fun isHighRisk(): Boolean {
        return isNegativeSpiralDetected || 
               (sentiment == SentimentType.NEGATIVE && confidenceScore > 0.8f) ||
               primaryEmotion.lowercase() in listOf("depression", "despair", "hopelessness")
    }
    
    /**
     * Get summary for quick display
     */
    fun getSummary(): String {
        val spiralFlag = if (isNegativeSpiralDetected) " ⚠️" else ""
        return "${primaryEmotion.replaceFirstChar { it.uppercase() }} (${sentiment.displayName})$spiralFlag"
    }
}

@Serializable
enum class SentimentType(val displayName: String) {
    POSITIVE("Positive"),
    NEUTRAL("Neutral"), 
    NEGATIVE("Negative")
}

@Serializable
enum class ProcessingMethod(val displayName: String) {
    MULTIMODAL_AUDIO("Direct Audio"),
    TEXT_BASED("Text Analysis"),
    HYBRID("Audio + Text")
}

@Serializable
enum class EmotionalIntensity(val displayName: String, val level: Int) {
    VERY_LOW("Very Low", 1),
    LOW("Low", 2),
    MODERATE("Moderate", 3),
    HIGH("High", 4),
    VERY_HIGH("Very High", 5)
}

@Serializable
enum class Valence(val displayName: String) {
    VERY_NEGATIVE("Very Negative"),
    NEGATIVE("Negative"),
    NEUTRAL("Neutral"),
    POSITIVE("Positive"),
    VERY_POSITIVE("Very Positive")
}

@Serializable
enum class Arousal(val displayName: String) {
    VERY_LOW("Very Calm"),
    LOW("Calm"),
    MEDIUM("Moderate"),
    HIGH("Energetic"),
    VERY_HIGH("Very Energetic")
}

/**
 * Data class for sentiment analysis statistics and trends
 */
@Serializable
data class SentimentAnalysisStats(
    val totalAnalyses: Int,
    val negativeSpiralCount: Int,
    val averageConfidence: Float,
    val mostCommonEmotion: String,
    val mostCommonTriggers: List<String>,
    val trendDirection: TrendDirection,
    val timeRange: String
)

@Serializable
enum class TrendDirection {
    IMPROVING,
    STABLE,
    DECLINING,
    UNKNOWN
} 


