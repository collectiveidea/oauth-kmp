package com.collectiveidea.oauth

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets

class TestPlatformPKCEFlow(
    val automaticallyInvokeCompletionCallback: Boolean = false,
    val simulateError: Boolean = false,
) : PlatformPKCEFlow {
    override fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
        completionHandler: (String?, String?) -> Unit,
    ) {
        if (automaticallyInvokeCompletionCallback) {
            if (simulateError) {
                completionHandler(null, "Simulated External Authorization Error")
            } else {
                completionHandler("$redirectUrl?code=the-auth-code", null)
            }
        }
    }
}

private fun createOAuthHttpClient(engine: HttpClientEngine) = HttpClient(engine) {
    installJsonOAuth("https://localhost/")
}

fun createOAuthService(engine: HttpClientEngine): OAuthService = OAuthServiceImpl(
    createOAuthHttpClient(engine),
    "example_client_id",
)

fun MockRequestHandleScope.respondWithOAuthTokens(includeScope: Boolean = false): HttpResponseData {
    val scope: String = if (includeScope) ",\"scope\":\"public write\"" else ""
    return respond(
        """
        {
            "access_token":"access_token_value",
            "token_type":"Bearer",
            "expires_in":7200,
            "refresh_token":"refresh_token_value",
            "created_at":1661450918$scope
        }
        """.trimIndent(),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}

fun MockRequestHandleScope.respondWithOAuthError(): HttpResponseData = respond(
    """
        {
            "error":"invalid_grant",
            "error_description":"The provided authorization grant is invalid, expired, revoked, does not match the redirection URI used in the authorization request, or was issued to another client."
        }
    """.trimIndent(),
    HttpStatusCode.BadRequest,
    headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json
            .withCharset(Charsets.UTF_8)
            .toString(),
    ),
)
