package com.collectiveidea.oauth

import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json

/**
 * Configures the client for a JSON OAuth service. Every error response is thrown as an
 * [OAuthException]: its [OAuthError] is decoded from the JSON body, or, when the body isn't JSON
 * or doesn't decode as an [OAuthError], has the code `unknown` and the raw body as its description.
 */
public fun HttpClientConfig<*>.installJsonOAuth() {
    expectSuccess = true

    defaultRequest {
        accept(ContentType.Application.Json)
        contentType(ContentType.Application.Json)
    }

    install(ContentNegotiation) {
        json()
    }

    // Intercept exceptions to extract JSON message content before passing the exceptions along
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            if (exception !is ResponseException) {
                // Should never happen, except maybe in comparison failures when running unit tests.
                throw exception
            }

            val error = exception.response.decodeOAuthErrorOrNull()
                ?: OAuthError("unknown", exception.response.bodyAsText())

            throw OAuthException(error, exception)
        }
    }
}

private suspend fun HttpResponse.decodeOAuthErrorOrNull(): OAuthError? {
    if (!isJson()) return null

    return try {
        body()
    } catch (e: ContentConvertException) {
        null
    }
}

internal fun ContentType.isJson() = match(ContentType.Application.Json)

internal fun HttpResponse.isJson() = contentType()?.isJson() == true
