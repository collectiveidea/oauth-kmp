package com.collectiveidea.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * An OAuth 2.0 error response, per [RFC 6749 §5.2](https://datatracker.ietf.org/doc/html/rfc6749#section-5.2).
 *
 * @property code The `error` code, such as `invalid_grant`; `unknown` when [installJsonOAuth] couldn't decode an
 *  error from the response body.
 * @property description The human-readable `error_description`, or null when the server sent none.
 */
@Serializable
@JsonIgnoreUnknownKeys
public data class OAuthError(
    @SerialName("error") val code: String,
    @SerialName("error_description") val description: String? = null,
)
