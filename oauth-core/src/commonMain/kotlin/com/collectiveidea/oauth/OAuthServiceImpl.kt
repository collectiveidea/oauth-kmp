package com.collectiveidea.oauth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

/**
 * An [OAuthService] that calls the server's token endpoint.
 *
 * @param httpClient A pre-configured JSON-OAuth-ready HttpClient. Use the
 *   [com.collectiveidea.oauth.installJsonOAuth] helper for easy client creation, e.g.
 *
 *   val client = HttpClient(engine) {
 *      installJsonOAuth()
 *   }
 *
 * @param clientId The OAuth client's id, sent with every token request.
 * @param tokenEndpoint The server's token endpoint as a full URL, such as "https://www.example.com/oauth/token".
 */
public class OAuthServiceImpl(
    private val httpClient: HttpClient,
    override val clientId: String,
    private val tokenEndpoint: String,
) : OAuthService {
    override suspend fun exchangeAuthorizationCode(
        code: String,
        verifier: String,
        redirectUrl: String,
    ): TokenResponse {
        val response: HttpResponse = httpClient.post(tokenEndpoint) {
            setBody(
                AuthorizationCodeRequest(
                    clientId = clientId,
                    code = code,
                    verifier = verifier,
                    redirectUrl = redirectUrl,
                ),
            )
        }

        return response.body()
    }

    override suspend fun refreshTokens(refreshToken: String): TokenResponse {
        val response: HttpResponse = httpClient.post(tokenEndpoint) {
            setBody(
                RefreshTokensRequest(
                    clientId = clientId,
                    refreshToken = refreshToken,
                ),
            )
        }

        return response.body()
    }
}
