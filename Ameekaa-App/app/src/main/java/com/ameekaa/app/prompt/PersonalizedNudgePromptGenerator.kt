package com.ameekaa.app.prompt

import android.content.Context
import android.util.Log
import com.ameekaa.app.data.model.*
import com.ameekaa.app.data.store.*
import com.ameekaa.app.AudioAnalysisResult
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.first

/**
 * PersonalizedNudgePromptGenerator - Creates comprehensive prompts for Gemma model
 * 
 * This class generates personalized nudge prompts by combining:
 * - User's Starting Point Data (depression assessment)
 * - Personality Preferences (OCEAN traits)
 * - Joy Toolkit (personalized coping tools)
 * - Current Sentiment Analysis (from audio)
 */
class PersonalizedNudgePromptGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "NudgePromptGenerator"
    }
    
    /**
     * Generates a comprehensive prompt for Gemma model to create a personalized nudge
     */
    suspend fun generateNudgePrompt(
        userId: String,
        sentimentAnalysis: AudioAnalysisResult,
        userProfileDataStore: UserProfileDataStore,
        startingPointDataStore: StartingPointDataStore,
        personalityPreferencesDataStore: PersonalityPreferencesDataStore,
        joyToolkitDataStore: JoyToolkitDataStore
    ): String {
        
        Log.i(TAG, "🎯 Generating personalized nudge prompt for user: $userId")
        
        // Gather all user data using Flow
        val userProfile = userProfileDataStore.userProfiles.first().find { it.userId == userId }
        val startingPointData = startingPointDataStore.startingPointData.first().find { it.userId == userId }
        val personalityData = personalityPreferencesDataStore.personalityPreferencesData.first().find { it.userId == userId }
        val joyToolkitData = joyToolkitDataStore.joyToolkitData.first().find { it.userId == userId }
        
        if (userProfile == null || startingPointData == null || personalityData == null || joyToolkitData == null) {
            Log.e(TAG, "❌ Missing user data for userId: $userId")
            return generateFallbackPrompt(sentimentAnalysis)
        }
        
        Log.i(TAG, "✅ All user data gathered successfully")
        
        // Calculate personality traits
        val personalityTraits = personalityData.getPersonalityTraits()
        val personalityProfile = personalityData.getPersonalityProfile()
        
        // Calculate depression metrics
        val depressionScore = startingPointData.calculateDepressionScore()
        val severityLevel = startingPointData.getDepressionSeverityLevel()
        
        return createComprehensivePrompt(
            userProfile = userProfile,
            startingPointData = startingPointData,
            personalityTraits = personalityTraits,
            personalityProfile = personalityProfile,
            joyToolkitData = joyToolkitData,
            sentimentAnalysis = sentimentAnalysis,
            depressionScore = depressionScore,
            severityLevel = severityLevel
        )
    }
    
    /**
     * Creates the comprehensive prompt for Gemma model
     */
    private fun createComprehensivePrompt(
        userProfile: UserProfile,
        startingPointData: StartingPointData,
        personalityTraits: List<PersonalityTraitScore>,
        personalityProfile: PersonalityPreferencesData.PersonalityProfile,
        joyToolkitData: JoyToolkitData,
        sentimentAnalysis: AudioAnalysisResult,
        depressionScore: Int,
        severityLevel: StartingPointData.DepressionSeverityLevel
    ): String {
        
        return """
        You are Amica — a hyper-personalized emotional wellness companion trained to provide compassionate, intelligent support. Your task is to create a personalized nudge for ${userProfile.userName} based on their comprehensive profile and current emotional state.

        ## USER PROFILE
        **Name**: ${userProfile.userName}
        **User ID**: ${userProfile.userId}

        ## CURRENT EMOTIONAL STATE (from Audio Sentiment Analysis)
        **Primary Emotion**: ${sentimentAnalysis.emotionSentiment.primaryEmotion}
        **Sentiment**: ${sentimentAnalysis.emotionSentiment.sentiment}
        **Confidence Score**: ${sentimentAnalysis.emotionSentiment.confidenceScore}
        
        **Negative Spiral Detection**: ${if (sentimentAnalysis.negativeSpiralDetection.isNegativeSpiralDetected) "DETECTED" else "Not detected"}
        ${if (sentimentAnalysis.negativeSpiralDetection.isNegativeSpiralDetected) 
            "- **Detected Triggers**: ${sentimentAnalysis.negativeSpiralDetection.detectedTriggers.joinToString(", ")}\n- **Analysis**: ${sentimentAnalysis.negativeSpiralDetection.reasoning}" 
            else ""}
        
        **Key Themes from Audio**: ${sentimentAnalysis.topicKeywordExtraction.keyThemes.joinToString(", ")}
        **Important Keywords**: ${sentimentAnalysis.topicKeywordExtraction.importantKeywords.joinToString(", ")}

        ## DEPRESSION ASSESSMENT (Starting Point Data)
        **Depression Score**: ${depressionScore}/21 (${severityLevel.name.lowercase().replace("_", " ")})
        **Detailed Assessment**:
        - No positive feelings: ${startingPointData.noPositiveFeelings}/3
        - Lack of initiative: ${startingPointData.lackOfInitiative}/3
        - Nothing to look forward to: ${startingPointData.nothingToLookForward}/3
        - Feeling blue: ${startingPointData.feelingBlue}/3
        - Lack of enthusiasm: ${startingPointData.lackOfEnthusiasm}/3
        - Low self-worth: ${startingPointData.lowSelfWorth}/3
        - Life meaningless: ${startingPointData.lifeMeaningless}/3

        ## PERSONALITY PROFILE (OCEAN Analysis)
        ${personalityTraits.map { trait ->
            "**${trait.trait.name}**: ${String.format("%.1f", trait.score)}/1.0 - ${trait.getInterpretation()}"
        }.joinToString("\n")}

        **Recommended Support Strategies**:
        ${personalityProfile.supportStrategies.map { "- $it" }.joinToString("\n")}

        ## PERSONALIZED JOY TOOLKIT
        **Sensory Tools**:
        - Sight: ${joyToolkitData.sensoryToolkit.sight}
        - Sound: ${joyToolkitData.sensoryToolkit.sound}

        **Activity Tools**:
        - Hobbies & Passions: ${joyToolkitData.activityToolkit.hobbiesAndPassions}
        - Movement: ${joyToolkitData.activityToolkit.powerOfMovement}

        **Mind Tools**:
        - Mental Reset: ${joyToolkitData.mindToolkit.mentalReset}
        - Self-Talk: "${joyToolkitData.mindToolkit.selfTalk}"
        - Curiosity Interest: ${joyToolkitData.mindToolkit.curiosity}
        - Focus Technique: ${joyToolkitData.mindToolkit.focusTools}

        **Meaning Tools**:
        - Core Value: ${joyToolkitData.meaningToolkit.values}

        ## TASK: CREATE PERSONALIZED NUDGE
        Based on all the above information, create a personalized, compassionate nudge for ${userProfile.userName} that:

        1. **Acknowledges their current emotional state** (sad, negative sentiment)
        2. **Addresses any detected negative spirals** with specific, gentle interventions
        3. **Leverages their personality traits** for maximum effectiveness
        4. **Incorporates their specific Joy Toolkit tools** that are most relevant to their current state
        5. **Considers their depression severity level** (${severityLevel.name.lowercase().replace("_", " ")}) for appropriate intervention intensity
        6. **Provides 2-3 specific, actionable micro-steps** they can take right now

        **Response Format**:
        ```json
        {
            "nudge_message": "Personalized message for ${userProfile.userName}",
            "recommended_actions": [
                "Specific action 1 using their tools",
                "Specific action 2 based on personality",
                "Specific action 3 addressing current sentiment"
            ],
            "toolkit_integration": {
                "primary_tool_category": "sensory/activity/mind/meaning",
                "specific_tool": "exact tool from their profile",
                "why_this_tool": "reasoning based on current state and personality"
            },
            "follow_up_strategy": "Suggested approach for continued support"
        }
        ```

        Remember: 
        - Use ${userProfile.userName}'s name naturally throughout
        - Reference their specific tools (e.g., "${joyToolkitData.sensoryToolkit.sight}" for sight, "${joyToolkitData.mindToolkit.selfTalk}" for self-talk)
        - Match the intervention intensity to their depression level
        - Consider their personality preferences (e.g., ${if (personalityTraits.find { it.trait.name == "EXTRAVERSION" }?.score ?: 0.5f > 0.5f) "they're more extraverted, suggest social connection" else "they're more introverted, respect their need for solitude"})
        - Address the specific triggers if negative spiral was detected: ${sentimentAnalysis.negativeSpiralDetection.detectedTriggers.joinToString(", ")}
        """.trimIndent()
    }
    
    /**
     * Generates a fallback prompt when user data is incomplete
     */
    private fun generateFallbackPrompt(sentimentAnalysis: AudioAnalysisResult): String {
        return """
        You are Amica — a compassionate emotional wellness companion. A user is experiencing:
        
        **Current State**: ${sentimentAnalysis.emotionSentiment.primaryEmotion} emotion with ${sentimentAnalysis.emotionSentiment.sentiment} sentiment
        
        **Negative Spiral**: ${if (sentimentAnalysis.negativeSpiralDetection.isNegativeSpiralDetected) "Detected" else "Not detected"}
        ${if (sentimentAnalysis.negativeSpiralDetection.isNegativeSpiralDetected) 
            "Triggers: ${sentimentAnalysis.negativeSpiralDetection.detectedTriggers.joinToString(", ")}" else ""}
        
        **Key Themes**: ${sentimentAnalysis.topicKeywordExtraction.keyThemes.joinToString(", ")}
        
        Create a gentle, supportive nudge with 2-3 specific actions they can take right now to improve their emotional state.
        """.trimIndent()
    }
}

/**
 * Data class for structured nudge response from Gemma model
 */
@Serializable
data class PersonalizedNudgeResponse(
    val nudgeMessage: String,
    val recommendedActions: List<String>,
    val toolkitIntegration: ToolkitIntegration,
    val followUpStrategy: String
)

@Serializable
data class ToolkitIntegration(
    val primaryToolCategory: String,
    val specificTool: String,
    val whyThisTool: String
) 


