package com.collectiveidea.oauth

import app.cash.turbine.turbineScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.Url
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class PKCEFlowTest {
    @Test
    fun `buildSignInUrl returns correct URL`() {
        val pkceFlow = PKCEFlow(
            TestPlatformPKCEFlow(),
            createOAuthService(
                MockEngine {
                    fail("should not be invoked")
                },
            ),
            oauthBaseUrl = "https://www.example.com/path/",
            redirectUrl = "exampleapp://oauth",
            MainScope(),
            Dispatchers.IO,
            // We use a known Random seed of 1234 to always get the same code challenge under test
            random = Random(1234),
        )

        val url = Url(pkceFlow.buildSignInUrl())

        assertEquals("www.example.com", url.host)
        assertEquals("/path/oauth/authorize", url.encodedPath)

        val parameters = url.parameters

        assertEquals("example_client_id", parameters["client_id"])
        // TRICKY: parameters.get will URL-decode `exampleapp%3A%2F%2Fauth`
        assertEquals("exampleapp://oauth", parameters["redirect_uri"])
        // TRICKY: parameters.get will URL-decode `public+write`
        assertEquals("public write", parameters["scope"])
        assertEquals("XFZKmPQOu1pBFhonCwSRnksNVNGsSigoVZObIQa1ngU", parameters["code_challenge"])
        assertEquals("S256", parameters["code_challenge_method"])
    }

    @Test
    fun `flow happy path completes successfully`() = runTest {
        val pkceFlow = PKCEFlow(
            TestPlatformPKCEFlow(
                automaticallyInvokeCompletionCallback = true,
            ),
            createOAuthService(
                MockEngine {
                    respondWithOAuthTokens()
                },
            ),
            oauthBaseUrl = "https://www.example.com/path/",
            redirectUrl = "exampleapp://oauth",
            externalScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            ioDispatcher = Dispatchers.IO,
        )

        turbineScope {
            val turbine = pkceFlow.authState.testIn(backgroundScope)

            // Initial state

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.NOT_STARTED,
                ),
                turbine.awaitItem(),
            )

            // Start the sign-in process

            pkceFlow.startSignIn()

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.WAITING_FOR_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            // In this scenario the sign in completion callback is automatically invoked
            // with a valid callback url containing an authorization code.

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.EXCHANGING_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.FINISHED,
                    tokenResponse = TokenResponse(
                        accessToken = "access_token_value",
                        tokenType = "Bearer",
                        expiresIn = 7200,
                        refreshToken = "refresh_token_value",
                        createdAt = 1661450918,
                        scope = null,
                    ),
                    errorMessage = null,
                ),
                turbine.awaitItem(),
            )

            // Clear the token response from memory now that we've examined it.

            pkceFlow.resetState()

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.NOT_STARTED,
                ),
                turbine.awaitItem(),
            )

            turbine.ensureAllEventsConsumed()
        }
    }

    @Test
    fun `flow happy path completes successfully when manually invoking completion callback`() = runTest {
        val pkceFlow = PKCEFlow(
            TestPlatformPKCEFlow(
                automaticallyInvokeCompletionCallback = false,
            ),
            createOAuthService(
                MockEngine {
                    respondWithOAuthTokens()
                },
            ),
            oauthBaseUrl = "https://www.example.com/path/",
            redirectUrl = "exampleapp://oauth",
            externalScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            ioDispatcher = Dispatchers.IO,
        )

        turbineScope {
            val turbine = pkceFlow.authState.testIn(backgroundScope)

            // Initial state

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.NOT_STARTED,
                ),
                turbine.awaitItem(),
            )

            // Start the sign-in process

            pkceFlow.startSignIn()

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.WAITING_FOR_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            // In this scenario the sign in completion callback must be manually invoked

            pkceFlow.continueSignInWithCallbackOrError("exampleapp://oauth?code=fake-code", null)

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.EXCHANGING_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.FINISHED,
                    tokenResponse = TokenResponse(
                        accessToken = "access_token_value",
                        tokenType = "Bearer",
                        expiresIn = 7200,
                        refreshToken = "refresh_token_value",
                        createdAt = 1661450918,
                        scope = null,
                    ),
                    errorMessage = null,
                ),
                turbine.awaitItem(),
            )

            turbine.ensureAllEventsConsumed()
        }
    }

    @Test
    fun `flow reports error message when platform invoke callback with error message`() = runTest {
        val pkceFlow = PKCEFlow(
            TestPlatformPKCEFlow(
                automaticallyInvokeCompletionCallback = true,
                simulateError = true,
            ),
            createOAuthService(
                MockEngine {
                    fail("should not be invoked")
                },
            ),
            oauthBaseUrl = "https://www.example.com/path/",
            redirectUrl = "exampleapp://oauth",
            externalScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            ioDispatcher = Dispatchers.IO,
        )

        turbineScope {
            val turbine = pkceFlow.authState.testIn(backgroundScope)

            // Initial state

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.NOT_STARTED,
                ),
                turbine.awaitItem(),
            )

            // Start the sign-in process

            pkceFlow.startSignIn()

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.WAITING_FOR_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            // In this scenario the sign in completion callback is automatically invoked
            // and an error message is supplied by the platform instead of a valid code.

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.FINISHED,
                    tokenResponse = null,
                    errorMessage = "Simulated External Authorization Error",
                ),
                turbine.awaitItem(),
            )

            turbine.ensureAllEventsConsumed()
        }
    }

    @Test
    fun `flow reports error message when server rejects authorization code`() = runTest {
        val pkceFlow = PKCEFlow(
            TestPlatformPKCEFlow(
                automaticallyInvokeCompletionCallback = true
            ),
            createOAuthService(
                MockEngine {
                    respondWithOAuthError()
                },
            ),
            oauthBaseUrl = "https://www.example.com/path/",
            redirectUrl = "exampleapp://oauth",
            externalScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            ioDispatcher = Dispatchers.IO,
        )

        turbineScope {
            val turbine = pkceFlow.authState.testIn(backgroundScope)

            // Initial state

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.NOT_STARTED,
                ),
                turbine.awaitItem(),
            )

            // Start the sign-in process

            pkceFlow.startSignIn()

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.WAITING_FOR_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            // In this scenario the sign in completion callback is automatically invoked
            // with an authorization code, but the code exchange fails.

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.EXCHANGING_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.FINISHED,
                    tokenResponse = null,
                    errorMessage = "The provided authorization grant is invalid, expired, revoked, does not match the redirection URI used in the authorization request, or was issued to another client.",
                ),
                turbine.awaitItem(),
            )

            turbine.ensureAllEventsConsumed()
        }
    }

    @Test
    fun `flow reports error message when authorization code fails`() = runTest {
        val pkceFlow = PKCEFlow(
            TestPlatformPKCEFlow(
                automaticallyInvokeCompletionCallback = true
            ),
            createOAuthService(
                MockEngine {
                    throw IOException("Simulated Network Error")
                },
            ),
            oauthBaseUrl = "https://www.example.com/path/",
            redirectUrl = "exampleapp://oauth",
            externalScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            ioDispatcher = Dispatchers.IO,
        )

        turbineScope {
            val turbine = pkceFlow.authState.testIn(backgroundScope)

            // Initial state

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.NOT_STARTED,
                ),
                turbine.awaitItem(),
            )

            // Start the sign-in process

            pkceFlow.startSignIn()

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.WAITING_FOR_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            // In this scenario the sign in completion callback is automatically invoked
            // with an authorization code, but the code exchange fails.

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.EXCHANGING_AUTHORIZATION_CODE,
                ),
                turbine.awaitItem(),
            )

            assertEquals(
                PKCEFlow.PKCEAuthState(
                    state = PKCEFlow.PKCEAuthState.State.FINISHED,
                    tokenResponse = null,
                    errorMessage = "Simulated Network Error",
                ),
                turbine.awaitItem(),
            )

            turbine.ensureAllEventsConsumed()
        }
    }
}
