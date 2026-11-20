package com.collectiveidea.oauth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters

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
    ): TokenResponse = httpClient
        .submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("client_id", clientId)
                append("code", code)
                append("code_verifier", verifier)
                append("redirect_uri", redirectUrl)
            },
        ).body()

    override suspend fun refreshTokens(refreshToken: String): TokenResponse = httpClient
        .submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("client_id", clientId)
                append("refresh_token", refreshToken)
            },
        ).body()
}
