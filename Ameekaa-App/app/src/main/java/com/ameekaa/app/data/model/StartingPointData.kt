package com.ameekaa.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StartingPointData(
    val userId: String,
    val noPositiveFeelings: Int,        // Question 1
    val lackOfInitiative: Int,          // Question 2
    val nothingToLookForward: Int,      // Question 3
    val feelingBlue: Int,               // Question 4
    val lackOfEnthusiasm: Int,          // Question 5
    val lowSelfWorth: Int,              // Question 6
    val lifeMeaningless: Int            // Question 7
) {
    fun calculateDepressionScore(): Int {
        val sum = noPositiveFeelings +
               lackOfInitiative +
               nothingToLookForward +
               feelingBlue +
               lackOfEnthusiasm +
               lowSelfWorth +
               lifeMeaningless
        return sum * 2
    }

    fun getDepressionSeverityLevel(): DepressionSeverityLevel {
        val score = calculateDepressionScore()
        return when {
            score <= 9 -> DepressionSeverityLevel.NORMAL
            score <= 13 -> DepressionSeverityLevel.MILD
            score <= 20 -> DepressionSeverityLevel.MODERATE
            score <= 27 -> DepressionSeverityLevel.SEVERE
            else -> DepressionSeverityLevel.EXTREMELY_SEVERE
        }
    }

    @Serializable
    enum class DepressionSeverityLevel {
        NORMAL,           // 0-9
        MILD,            // 10-13
        MODERATE,        // 14-20
        SEVERE,          // 21-27
        EXTREMELY_SEVERE // 28+
    }
} 


