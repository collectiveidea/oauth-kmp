package com.collectiveidea.oauth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

val jsonDecoder = Json

class OAuthServiceTest {
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
        assertEquals(1661450918, response.createdAt)
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
        assertEquals(1661450918, response.createdAt)
        assertNull(response.scope)
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
}
