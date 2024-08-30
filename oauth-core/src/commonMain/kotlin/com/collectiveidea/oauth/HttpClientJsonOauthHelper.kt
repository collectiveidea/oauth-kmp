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
import io.ktor.serialization.kotlinx.json.json

/**
 * @param baseUrl The base URL of the JSON OAuth service, everything before `/oauth/<verb>` in
 *  the URL, and with a trailing slash: e.g. `https://www.example.com/path/`
 */
fun HttpClientConfig<*>.installJsonOAuth(baseUrl: String) {
    expectSuccess = true

    defaultRequest {
        url(baseUrl)

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

            val error: OAuthError = if (exception.response.isJson()) {
                exception.response.body()
            } else {
                OAuthError("unknown", exception.response.bodyAsText())
            }

            throw OAuthException(error, exception)
        }
    }
}

fun ContentType.isJson() = match(ContentType.Application.Json)

fun HttpResponse.isJson() = contentType()?.isJson() == true
