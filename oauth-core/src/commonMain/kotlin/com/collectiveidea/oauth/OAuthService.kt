package com.collectiveidea.oauth

import kotlin.coroutines.cancellation.CancellationException

public interface OAuthService {
    public val clientId: String

    /**
     * Exchanges the code parameter affixed to the PKCE `redirect_uri` for access/refresh tokens.
     *
     * @param code The code parameter extracted from the `redirect_uri`
     * @param verifier The original verifier used to generate the `code_challenge` that was included
     *   as a sign-in URL query parameter.
     * @param redirectUrl The original `redirect_uri` that was included as a sign-in URL query parameter.
     */
    @Throws(OAuthException::class, CancellationException::class)
    public suspend fun exchangeAuthorizationCode(code: String, verifier: String, redirectUrl: String): TokenResponse

    /**
     * Meant to be called when an access_token is no longer value, to get new access/refresh tokens.
     *
     * @param refreshToken The refresh token of a previous [TokenResponse]
     */
    @Throws(OAuthException::class, CancellationException::class)
    public suspend fun refreshTokens(refreshToken: String): TokenResponse
}
