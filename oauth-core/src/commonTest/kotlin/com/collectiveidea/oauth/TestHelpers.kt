package com.collectiveidea.oauth

import com.chrynan.krypt.csprng.SecureRandom
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.random.Random
import kotlin.test.fail

class TestWebAuthSession(
    private val completionHandler: WebAuthSessionCompletionHandler,
    val automaticallyInvokeCompletionCallback: Boolean = false,
    val simulateError: Boolean = false,
) : WebAuthSession {
    override fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
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

/**
 * Builds the factory that [PKCEFlow] expects: [PKCEFlow] supplies the completion handler, and this
 * returns a [TestWebAuthSession] wired to it.
 */
fun testWebAuthSession(
    automaticallyInvokeCompletionCallback: Boolean = false,
    simulateError: Boolean = false,
): WebAuthSessionFactory = WebAuthSessionFactory { completionHandler ->
    TestWebAuthSession(completionHandler, automaticallyInvokeCompletionCallback, simulateError)
}

private fun createOAuthHttpClient(engine: HttpClientEngine) = HttpClient(engine) {
    installJsonOAuth()
}

fun createPKCEFlow(
    applicationScope: CoroutineScope,
    webAuthSessionFactory: WebAuthSessionFactory = testWebAuthSession(),
    engine: HttpClientEngine = MockEngine { fail("token exchange should not be attempted") },
    authorizationEndpoint: String = "https://www.example.com/path/oauth/authorize",
    redirectUrl: String = "exampleapp://oauth",
    scopes: List<String> = emptyList(),
    random: Random = SecureRandom(),
): PKCEFlow = PKCEFlow(
    webAuthSessionFactory,
    createOAuthService(engine),
    authorizationEndpoint = authorizationEndpoint,
    redirectUrl = redirectUrl,
    applicationScope = applicationScope,
    ioDispatcher = Dispatchers.IO,
    scopes = scopes,
    random = random,
)

fun createOAuthService(
    engine: HttpClientEngine,
    tokenEndpoint: String = "https://localhost/oauth/token",
): OAuthService = OAuthServiceImpl(
    createOAuthHttpClient(engine),
    "example_client_id",
    tokenEndpoint,
)

fun MockRequestHandleScope.respondWithOAuthTokens(
    includeScope: Boolean = false,
    extraFields: String = "",
): HttpResponseData {
    val scope: String = if (includeScope) ",\"scope\":\"public write\"" else ""
    return respond(
        """
        {
            "access_token":"access_token_value",
            "token_type":"Bearer",
            "expires_in":7200,
            "refresh_token":"refresh_token_value",
            "created_at":1661450918$scope$extraFields
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

fun MockRequestHandleScope.respondWithErrorBody(
    status: HttpStatusCode,
    contentType: ContentType,
    content: String,
): HttpResponseData = respond(content, status, headersOf(HttpHeaders.ContentType, contentType.toString()))
