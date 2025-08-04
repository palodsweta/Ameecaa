package com.ameekaa.app.data.model

import kotlinx.serialization.Serializable
import com.ameekaa.app.data.model.PersonalityPreferencesData.PersonalityTrait

@Serializable
data class PersonalityTraitScore(
    val trait: PersonalityTrait,
    val score: Float
) {
    fun getInterpretation(): String {
        return when (trait) {
            PersonalityTrait.OPENNESS -> when {
                score >= 0.8 -> "Highly creative and open to new experiences. Values intellectual curiosity and artistic pursuits."
                score >= 0.6 -> "Moderately open to new experiences. Balances traditional and novel approaches."
                score >= 0.4 -> "Practical and grounded. Prefers familiar routines and concrete thinking."
                else -> "Traditional and practical. Strong preference for routine and familiar experiences."
            }
            PersonalityTrait.CONSCIENTIOUSNESS -> when {
                score >= 0.8 -> "Highly organized and detail-oriented. Strong focus on planning and goal achievement."
                score >= 0.6 -> "Reliable and organized. Good balance of planning and flexibility."
                score >= 0.4 -> "Moderately organized. Can be flexible with plans and schedules."
                else -> "Spontaneous and flexible. Prefers to go with the flow rather than strict planning."
            }
            PersonalityTrait.EXTRAVERSION -> when {
                score >= 0.8 -> "Highly sociable and energized by social interaction. Seeks out group activities."
                score >= 0.6 -> "Moderately extraverted. Enjoys social situations while also valuing alone time."
                score >= 0.4 -> "Tends toward introversion. Prefers smaller groups and quiet environments."
                else -> "Strongly introverted. Values solitude and one-on-one interactions."
            }
            PersonalityTrait.AGREEABLENESS -> when {
                score >= 0.8 -> "Highly empathetic and focused on others' emotional needs. Prioritizes harmony."
                score >= 0.6 -> "Generally cooperative and considerate. Balances others' needs with practicality."
                score >= 0.4 -> "Practical and objective in approach. Focuses on solutions over emotional support."
                else -> "Very analytical and logic-focused. Prioritizes truth and efficiency over feelings."
            }
            PersonalityTrait.NEUROTICISM -> when {
                score >= 0.8 -> "Experiences frequent anxiety and emotional sensitivity. May need extra support during changes."
                score >= 0.6 -> "Moderately sensitive to stress and change. Benefits from preparation and support."
                score >= 0.4 -> "Generally stable emotionally. Handles most changes and stress well."
                else -> "Very emotionally stable. Adapts easily to change and manages stress well."
            }
        }
    }

    fun getSupportStrategies(): List<String> {
        return when (trait) {
            PersonalityTrait.OPENNESS -> when {
                score >= 0.6 -> listOf(
                    "Offer creative problem-solving opportunities",
                    "Share interesting articles or ideas",
                    "Encourage exploration of new perspectives"
                )
                else -> listOf(
                    "Provide practical, concrete examples",
                    "Maintain familiar routines",
                    "Break new concepts into manageable steps"
                )
            }
            PersonalityTrait.CONSCIENTIOUSNESS -> when {
                score >= 0.6 -> listOf(
                    "Help create detailed action plans",
                    "Set clear goals and milestones",
                    "Provide structure and organization"
                )
                else -> listOf(
                    "Keep plans flexible",
                    "Focus on short-term achievable goals",
                    "Allow for spontaneity"
                )
            }
            PersonalityTrait.EXTRAVERSION -> when {
                score >= 0.6 -> listOf(
                    "Encourage social connections",
                    "Suggest group activities",
                    "Create opportunities for discussion"
                )
                else -> listOf(
                    "Respect need for solitude",
                    "Prefer one-on-one interactions",
                    "Allow time to recharge"
                )
            }
            PersonalityTrait.AGREEABLENESS -> when {
                score >= 0.6 -> listOf(
                    "Focus on emotional validation",
                    "Emphasize harmony and cooperation",
                    "Acknowledge feelings first"
                )
                else -> listOf(
                    "Present logical analysis",
                    "Focus on practical solutions",
                    "Be direct and efficient"
                )
            }
            PersonalityTrait.NEUROTICISM -> when {
                score >= 0.6 -> listOf(
                    "Provide reassurance and support",
                    "Help prepare for changes",
                    "Break challenges into smaller steps"
                )
                else -> listOf(
                    "Encourage independence",
                    "Present challenges as opportunities",
                    "Support confident decision-making"
                )
            }
        }
    }
} 


