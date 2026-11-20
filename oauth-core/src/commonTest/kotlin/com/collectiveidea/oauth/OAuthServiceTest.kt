package com.collectiveidea.oauth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OAuthServiceTest {
    private val jsonDecoder = Json

    @Test
    fun `exchangeAuthorizationCode success`() = runTest {
        val mockEngine = MockEngine {
            assertEquals("https://localhost/oauth/token", it.url.toString())

            // Validate body request contains the correct information
            val request = jsonDecoder.decodeFromString<AuthorizationCodeRequest>((it.body as TextContent).text)
            assertEquals("example_client_id", request.clientId)
            assertEquals("example_code", request.code)
            assertEquals("example_verifier", request.verifier)
            assertEquals("authorization_code", request.grantType)
            assertEquals("exampleapp://auth", request.redirectUrl)

            respondWithOAuthTokens(true)
        }
        val oAuthService = createOAuthService(mockEngine)

        val response = oAuthService.exchangeAuthorizationCode("example_code", "example_verifier", "exampleapp://auth")

        assertEquals("access_token_value", response.accessToken)
        assertEquals("refresh_token_value", response.refreshToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(7200, response.expiresIn)
        assertEquals(1661450918L, response.createdAt)
        assertEquals("public write", response.scope)
    }

    @Test
    fun `exchangeAuthorizationCode invalid`() = runTest {
        val oAuthService = createOAuthService(MockEngine { respondWithOAuthError() })

        val exception = assertFailsWith<OAuthException> {
            oAuthService.exchangeAuthorizationCode("example_code", "fake_verifier", "exampleapp://auth")
        }
        val expectedError = OAuthError(
            "invalid_grant",
            "The provided authorization grant is invalid, expired, revoked, does not match the redirection URI used in the authorization request, or was issued to another client.",
        )
        assertEquals(expectedError, exception.error)
    }

    @Test
    fun `refreshTokens success`() = runTest {
        val mockEngine = MockEngine {
            assertEquals("https://localhost/oauth/token", it.url.toString())

            // Validate body request contains the correct information
            val request = jsonDecoder.decodeFromString<RefreshTokensRequest>((it.body as TextContent).text)
            assertEquals("the_valid_request_token", request.refreshToken)
            assertEquals("example_client_id", request.clientId)
            assertEquals("refresh_token", request.grantType)

            respondWithOAuthTokens()
        }
        val oAuthService = createOAuthService(mockEngine)

        val response = oAuthService.refreshTokens("the_valid_request_token")

        assertEquals("access_token_value", response.accessToken)
        assertEquals("refresh_token_value", response.refreshToken)
        assertEquals("Bearer", response.tokenType)
        assertEquals(7200, response.expiresIn)
        assertEquals(1661450918L, response.createdAt)
        assertNull(response.scope)
    }

    @Test
    fun `refreshTokens success with fields the client doesn't model`() = runTest {
        val extraFields = ""","id_token":"header.payload.signature","not_modeled":{"nested":[1,2]}"""
        val oAuthService = createOAuthService(MockEngine { respondWithOAuthTokens(extraFields = extraFields) })

        val response = oAuthService.refreshTokens("the_valid_request_token")

        assertEquals("access_token_value", response.accessToken)
        assertEquals("refresh_token_value", response.refreshToken)
    }

    @Test
    fun `refreshTokens success with only the fields RFC 6749 requires`() = runTest {
        val body = """{"access_token":"access_token_value","token_type":"Bearer"}"""
        val oAuthService = createOAuthService(
            MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) },
        )

        val response = oAuthService.refreshTokens("the_valid_request_token")

        assertEquals(TokenResponse(accessToken = "access_token_value", tokenType = "Bearer"), response)
    }

    @Test
    fun `refreshTokens success with a created_at past 2038`() = runTest {
        val body = """{"access_token":"access_token_value","token_type":"Bearer","created_at":2147483648}"""
        val oAuthService = createOAuthService(
            MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) },
        )

        val response = oAuthService.refreshTokens("the_valid_request_token")

        assertEquals(2147483648L, response.createdAt)
    }

    @Test
    fun `refreshTokens posts to the configured token endpoint`() = runTest {
        val oAuthService = createOAuthService(
            MockEngine {
                assertEquals("https://auth.example.net/v1/token", it.url.toString())
                respondWithOAuthTokens()
            },
            tokenEndpoint = "https://auth.example.net/v1/token",
        )

        val response = oAuthService.refreshTokens("the_valid_request_token")

        assertEquals("access_token_value", response.accessToken)
    }

    @Test
    fun `refreshTokens invalid`() = runTest {
        val oAuthService = createOAuthService(MockEngine { respondWithOAuthError() })

        val exception = assertFailsWith<OAuthException> {
            oAuthService.refreshTokens("invalid_refresh_token")
        }
        val expectedError = OAuthError(
            "invalid_grant",
            "The provided authorization grant is invalid, expired, revoked, does not match the redirection URI used in the authorization request, or was issued to another client.",
        )
        assertEquals(expectedError, exception.error)
    }

    @Test
    fun `refreshTokens invalid with an error_uri`() = runTest {
        val body = """{"error":"invalid_grant","error_description":"Token revoked.","error_uri":"https://localhost/docs/errors"}"""
        val oAuthService = createOAuthService(
            MockEngine { respondWithErrorBody(HttpStatusCode.BadRequest, ContentType.Application.Json, body) },
        )

        val exception = assertFailsWith<OAuthException> {
            oAuthService.refreshTokens("invalid_refresh_token")
        }
        assertEquals(OAuthError("invalid_grant", "Token revoked."), exception.error)
    }

    @Test
    fun `refreshTokens invalid without an error_description`() = runTest {
        val body = """{"error":"invalid_grant"}"""
        val oAuthService = createOAuthService(
            MockEngine { respondWithErrorBody(HttpStatusCode.BadRequest, ContentType.Application.Json, body) },
        )

        val exception = assertFailsWith<OAuthException> {
            oAuthService.refreshTokens("invalid_refresh_token")
        }
        assertEquals("invalid_grant", exception.error.code)
        assertNull(exception.error.description)
        assertEquals("invalid_grant", exception.message)
    }

    @Test
    fun `refreshTokens error with a non-JSON body`() = runTest {
        val body = "<html><body>502 Bad Gateway</body></html>"
        val oAuthService = createOAuthService(
            MockEngine { respondWithErrorBody(HttpStatusCode.BadGateway, ContentType.Text.Html, body) },
        )

        val exception = assertFailsWith<OAuthException> {
            oAuthService.refreshTokens("the_valid_request_token")
        }
        assertEquals(OAuthError("unknown", body), exception.error)
    }

    @Test
    fun `refreshTokens error with JSON that isn't an OAuth error`() = runTest {
        val body = """{"message":"Internal Server Error"}"""
        val oAuthService = createOAuthService(
            MockEngine { respondWithErrorBody(HttpStatusCode.InternalServerError, ContentType.Application.Json, body) },
        )

        val exception = assertFailsWith<OAuthException> {
            oAuthService.refreshTokens("the_valid_request_token")
        }
        assertEquals(OAuthError("unknown", body), exception.error)
    }

    @Test
    fun `refreshTokens error with malformed JSON`() = runTest {
        val body = """{"error":"""
        val oAuthService = createOAuthService(
            MockEngine { respondWithErrorBody(HttpStatusCode.BadRequest, ContentType.Application.Json, body) },
        )

        val exception = assertFailsWith<OAuthException> {
            oAuthService.refreshTokens("the_valid_request_token")
        }
        assertEquals(OAuthError("unknown", body), exception.error)
    }
}
