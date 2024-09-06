package com.collectiveidea.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class OAuthError(
    @SerialName("error") val code: String,
    @SerialName("error_description") val description: String,
)
