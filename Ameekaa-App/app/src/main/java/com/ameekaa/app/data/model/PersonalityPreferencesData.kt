package com.ameekaa.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PersonalityPreferencesData(
    val userId: String,
    val weekendChoice: WeekendChoice,
    val vacationChoice: VacationChoice,
    val meTimeChoice: MeTimeChoice,
    val problemSolvingChoice: ProblemSolvingChoice,
    val changeResponseChoice: ChangeResponseChoice
) {
    @Serializable
    enum class WeekendChoice {
        SOLO_EXPLORATION,    // Choice A: Openness/Introversion
        GROUP_ACTIVITY       // Choice B: Extraversion
    }

    @Serializable
    enum class VacationChoice {
        DETAILED_PLANNING,   // Choice A: Conscientiousness
        SPONTANEOUS         // Choice B: Openness/Low Conscientiousness
    }

    @Serializable
    enum class MeTimeChoice {
        CREATIVE_PROJECT,    // Choice A: Openness/Creativity
        ORGANIZING          // Choice B: Conscientiousness
    }

    @Serializable
    enum class ProblemSolvingChoice {
        EMOTIONAL_SUPPORT,   // Choice A: High Agreeableness/Empathy
        PRACTICAL_SOLUTIONS  // Choice B: Analytical/Lower Agreeableness
    }

    @Serializable
    enum class ChangeResponseChoice {
        ADAPTABLE,          // Choice A: Emotional Stability
        ANXIOUS             // Choice B: Higher Neuroticism
    }

    fun getPersonalityTraits(): List<PersonalityTraitScore> {
        val traits = mutableMapOf<PersonalityTrait, Float>()

        // Initialize all traits with a baseline of 0.5
        PersonalityTrait.values().forEach { traits[it] = 0.5f }

        // Adjust traits based on choices
        when (weekendChoice) {
            WeekendChoice.SOLO_EXPLORATION -> {
                traits[PersonalityTrait.OPENNESS] = traits[PersonalityTrait.OPENNESS]!! + 0.2f
                traits[PersonalityTrait.EXTRAVERSION] = traits[PersonalityTrait.EXTRAVERSION]!! - 0.2f
            }
            WeekendChoice.GROUP_ACTIVITY -> {
                traits[PersonalityTrait.EXTRAVERSION] = traits[PersonalityTrait.EXTRAVERSION]!! + 0.2f
            }
        }

        when (vacationChoice) {
            VacationChoice.DETAILED_PLANNING -> {
                traits[PersonalityTrait.CONSCIENTIOUSNESS] = traits[PersonalityTrait.CONSCIENTIOUSNESS]!! + 0.2f
            }
            VacationChoice.SPONTANEOUS -> {
                traits[PersonalityTrait.OPENNESS] = traits[PersonalityTrait.OPENNESS]!! + 0.2f
                traits[PersonalityTrait.CONSCIENTIOUSNESS] = traits[PersonalityTrait.CONSCIENTIOUSNESS]!! - 0.2f
            }
        }

        when (meTimeChoice) {
            MeTimeChoice.CREATIVE_PROJECT -> {
                traits[PersonalityTrait.OPENNESS] = traits[PersonalityTrait.OPENNESS]!! + 0.2f
            }
            MeTimeChoice.ORGANIZING -> {
                traits[PersonalityTrait.CONSCIENTIOUSNESS] = traits[PersonalityTrait.CONSCIENTIOUSNESS]!! + 0.2f
            }
        }

        when (problemSolvingChoice) {
            ProblemSolvingChoice.EMOTIONAL_SUPPORT -> {
                traits[PersonalityTrait.AGREEABLENESS] = traits[PersonalityTrait.AGREEABLENESS]!! + 0.2f
            }
            ProblemSolvingChoice.PRACTICAL_SOLUTIONS -> {
                traits[PersonalityTrait.AGREEABLENESS] = traits[PersonalityTrait.AGREEABLENESS]!! - 0.2f
            }
        }

        when (changeResponseChoice) {
            ChangeResponseChoice.ADAPTABLE -> {
                traits[PersonalityTrait.NEUROTICISM] = traits[PersonalityTrait.NEUROTICISM]!! - 0.2f
            }
            ChangeResponseChoice.ANXIOUS -> {
                traits[PersonalityTrait.NEUROTICISM] = traits[PersonalityTrait.NEUROTICISM]!! + 0.2f
            }
        }

        // Convert to list of PersonalityTraitScore with normalized values
        return traits.map { (trait, score) ->
            PersonalityTraitScore(trait, score.coerceIn(0f, 1f))
        }
    }

    fun getPersonalityProfile(): PersonalityProfile {
        val traitScores = getPersonalityTraits()
        return PersonalityProfile(
            userId = userId,
            traitScores = traitScores,
            supportStrategies = traitScores.flatMap { it.getSupportStrategies() }.distinct()
        )
    }

    @Serializable
    enum class PersonalityTrait {
        OPENNESS,
        CONSCIENTIOUSNESS,
        EXTRAVERSION,
        AGREEABLENESS,
        NEUROTICISM
    }

    @Serializable
    data class PersonalityProfile(
        val userId: String,
        val traitScores: List<PersonalityTraitScore>,
        val supportStrategies: List<String>
    )
} 


