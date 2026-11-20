package com.collectiveidea.oauth

import io.ktor.client.plugins.ResponseException

/**
 * Thrown for an error response from the OAuth service. Its message is the error's [OAuthError.description], or its
 * [OAuthError.code] when the server sent no description.
 */
public class OAuthException(
    public val error: OAuthError,
    responseException: ResponseException,
) : RuntimeException(error.description ?: error.code, responseException)
