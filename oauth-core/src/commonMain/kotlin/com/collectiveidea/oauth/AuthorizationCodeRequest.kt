package com.collectiveidea.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthorizationCodeRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("grant_type") val grantType: String = "authorization_code",
    @SerialName("code") val code: String,
    @SerialName("code_verifier") val verifier: String,
    @SerialName("redirect_uri") val redirectUrl: String,
)
