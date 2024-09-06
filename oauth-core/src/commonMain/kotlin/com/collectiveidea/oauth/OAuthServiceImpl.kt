package com.collectiveidea.oauth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

/**
 * @param httpClient A pre-configured JSON-OAuth-ready HttpClient. Use the
 *   [com.collectiveidea.oauth.installJsonOAuth] helper for easy client creation, e.g.
 *
 *   val client = HttpClient(engine) {
 *      installJsonOAuth(baseUrl)
 *   }
 *
 *  @param oauthBaseUrl The base URL, with trailing slash, for OAuth requests, e.g. "https://www.example.com/path/"
 *  @param redirectUrl The PKCE redirect URL that the server will redirect the user to on successful
 *    auth. The redirect URL is used to transfer control from the external web view in the PKCE flow
 *    back to the native application. On successful auth, the URL will have a "code" query parameter
 *    appended that can be exchanged for access/refresh tokens via exchangeAuthorizationCode.
 */
public class OAuthServiceImpl(
    private val httpClient: HttpClient,
    override val clientId: String,
) : OAuthService {
    override suspend fun exchangeAuthorizationCode(code: String, verifier: String, redirectUrl: String): TokenResponse {
        val response: HttpResponse = httpClient.post("oauth/token") {
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
        val response: HttpResponse = httpClient.post("oauth/token") {
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
