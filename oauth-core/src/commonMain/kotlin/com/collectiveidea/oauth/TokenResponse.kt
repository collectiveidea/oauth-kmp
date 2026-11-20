package com.collectiveidea.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * A successful access token response, per [RFC 6749 §5.1](https://datatracker.ietf.org/doc/html/rfc6749#section-5.1).
 *
 * @property expiresIn The access token's lifetime in seconds, or null when the server didn't send `expires_in`, which
 *  the RFC recommends but doesn't require.
 * @property createdAt When the token was issued, in seconds since the Unix epoch, or null when the server didn't send
 *  `created_at`. It's a Doorkeeper extension, not part of the RFC.
 */
@Serializable
@JsonIgnoreUnknownKeys
public data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("created_at") val createdAt: Int? = null,
    @SerialName("scope") val scope: String? = null,
)
