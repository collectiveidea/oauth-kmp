package com.collectiveidea.oauth

import io.ktor.client.plugins.ResponseException

class OAuthException(
    val error: OAuthError,
    responseException: ResponseException,
) : RuntimeException(error.description, responseException)
