package com.collectiveidea.oauth

import com.chrynan.krypt.csprng.SecureRandom
import io.ktor.http.URLParserException
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Encapsulates the PKCE Flow.
 *
 * This class is typically a singleton, since only one PKCE Flow happens at a time.
 *
 * @param platformPKCEFlow The platform-native implementation that handles the external auth session.
 * @param oauthService
 * @param oauthBaseUrl The fully qualified URL up until the "oauth" in the path. That is, if the sign
 *  in URL is "https://www.example.com/path/oauth/authorize", then this should be
 *  "https://www.example.com/path/"
 * @param redirectUrl The app-scheme URL and path that the external sign in process should use to transfer
 * control back to the application after external authorization session finishes.
 *  This must be the scheme that your app is registered to handle. A typical example is "exampleapp://auth"
 * @param externalScope The scope to use to launch the network request that exchanges the challenge code
 *  returned to the app (from the external authorization session) for the first set of access/refresh tokens.
 * @param ioDispatcher The coroutine context to move the network request to, typically `Dispatchers.IO`
 * @param random This should _always_ be `SecureRandom()`, but we allow constructor injection here in order
 *  to control the randomization values generated under test.
 */
class PKCEFlow(
    private val platformPKCEFlow: PlatformPKCEFlow,
    private val oauthService: OAuthService,
    val oauthBaseUrl: String,
    val redirectUrl: String,
    private val externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val random: Random = SecureRandom(),
) {
    private val pkce by lazy { PKCEUtil(random) }

    data class PKCEAuthState(
        val state: State = State.NOT_STARTED,
        val tokenResponse: TokenResponse? = null,
        val errorMessage: String? = null,
    ) {
        enum class State {
            NOT_STARTED,
            WAITING_FOR_AUTHORIZATION_CODE,
            EXCHANGING_AUTHORIZATION_CODE,
            FINISHED,
        }
    }

    private val _authState = MutableStateFlow(PKCEAuthState())
    val authState = _authState.asStateFlow()

    private var verifier: String? = null

    /**
     * Constructs the sign-in URL to open in an external web browser so that the user
     * can complete the OAuth PKCE flow on the external website.
     */
    internal fun buildSignInUrl(): String {
        // Re-generate the verifier on every request. Save the value, since we need it as
        // part of the exchange flow.
        verifier = pkce.generateCodeVerifier()

        val method = PKCEUtil.CodeChallengeMethod.S256
        val challenge = pkce.createCodeChallenge(verifier!!, method)

        // Could attempt to use KTor internals here to build the URL, but it's non-obvious so
        // we just roll our own since this is pretty simple.
        val authParams = mapOf(
            "client_id" to oauthService.clientId,
            "redirect_uri" to urlEncodedRedirectUrl,
            "response_type" to "code",
            "scope" to "public+write", // Uses + here instead of space to URL-encode
            "code_challenge" to challenge,
            "code_challenge_method" to method.typeName,
        ).map { (k, v) -> "$k=$v" }.joinToString("&")

        return "${oauthBaseUrl}oauth/authorize?$authParams"
    }

    // Supply our own trivial helper to URL-encode the redirect URL, since it needs to
    // be passed as a query param to the OAuth sign in URL.
    private val urlEncodedRedirectUrl: String get() {
        return redirectUrl.split("://").joinToString("%3A%2F%2F")
    }

    /**
     * Main entry point for the PKCE Flow. Transfers control of the auth process to the platform,
     * which opens the external browser to the appropriate sign in URL.
     */
    fun startSignIn() {
        _authState.update {
            PKCEAuthState(PKCEAuthState.State.WAITING_FOR_AUTHORIZATION_CODE)
        }

        val signInUrl = buildSignInUrl()
        platformPKCEFlow.startSignIn(signInUrl, redirectUrl, ::continueSignInWithCallbackOrError)
    }

    /**
     * The platform finishes the auth process and transfers control back to the application, which
     * in turn needs to transfer control back here to process the callback URL to swap the
     * authorization code for access/refresh tokens.
     */
    fun continueSignInWithCallbackOrError(
        callbackUrl: String?,
        errorMessage: String?,
    ) {
        if (errorMessage != null) {
            _authState.update {
                PKCEAuthState(
                    PKCEAuthState.State.FINISHED,
                    errorMessage = errorMessage,
                )
            }
        } else {
            val code = extractCodeFrom(callbackUrl)
            if (code.isNullOrBlank()) {
                _authState.update {
                    PKCEAuthState(
                        PKCEAuthState.State.FINISHED,
                        errorMessage = "Authorization code missing from external sign in.",
                    )
                }
            } else {
                _authState.update {
                    PKCEAuthState(
                        PKCEAuthState.State.EXCHANGING_AUTHORIZATION_CODE,
                    )
                }

                externalScope.launch {
                    exchangeAuthorizationCode(code)
                }
            }
        }
    }

    /**
     * Given a callback URL like `exampleapp://auth?code=1234abcd`, this method
     * extracts the code query param for use in [exchangeAuthorizationCode]
     */
    private fun extractCodeFrom(callbackUrl: String?): String? = try {
        callbackUrl?.let { Url(it).parameters }?.get("code")
    } catch (e: URLParserException) {
        null
    }

    private suspend fun exchangeAuthorizationCode(code: String) {
        try {
            val tokenResponse = withContext(ioDispatcher) {
                oauthService.exchangeAuthorizationCode(code, verifier!!, redirectUrl)
            }

            _authState.update {
                PKCEAuthState(
                    PKCEAuthState.State.FINISHED,
                    tokenResponse = tokenResponse,
                )
            }
        } catch (e: Exception) {
            _authState.update {
                PKCEAuthState(
                    PKCEAuthState.State.FINISHED,
                    errorMessage = e.message,
                )
            }
        }
    }

    /**
     * Resets the auth state back to NOT_STARTED. Typically called after encountering
     * a FINISHED state to clear the token response from memory.
     */
    fun resetState() {
        verifier = null

        _authState.update { PKCEAuthState() }
    }
}
