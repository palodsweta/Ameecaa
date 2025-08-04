package com.ameekaa.app.persona

data class TrustedPerson(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val relationship: String,
    val supportDescription: String,
    val phoneNumber: String? = null
) 


