package com.collectiveidea.oauth

import io.ktor.client.plugins.ResponseException

public class OAuthException(
    public val error: OAuthError,
    responseException: ResponseException,
) : RuntimeException(error.description, responseException)
