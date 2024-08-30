package com.collectiveidea.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthError(
    @SerialName("error") val code: String,
    @SerialName("error_description") val description: String,
)
