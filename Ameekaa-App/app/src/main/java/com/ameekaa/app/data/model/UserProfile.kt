package com.ameekaa.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val userId: String,
    val userName: String
) 


