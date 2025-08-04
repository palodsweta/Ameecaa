package com.ameekaa.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class JoyToolkitData(
    val userId: String,
    val sensoryToolkit: SensoryToolkit,
    val activityToolkit: ActivityToolkit,
    val mindToolkit: MindToolkit,
    val meaningToolkit: MeaningToolkit
) {
    @Serializable
    data class SensoryToolkit(
        val sight: String,
        val sound: String
    )

    @Serializable
    data class ActivityToolkit(
        val hobbiesAndPassions: String,
        val powerOfMovement: String
    )

    @Serializable
    data class MindToolkit(
        val mentalReset: String,
        val selfTalk: String,
        val curiosity: String,
        val focusTools: String
    )

    @Serializable
    data class MeaningToolkit(
        val values: String
    )
} 


