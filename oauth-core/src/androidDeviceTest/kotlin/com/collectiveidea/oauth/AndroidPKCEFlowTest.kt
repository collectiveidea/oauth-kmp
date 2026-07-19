package com.collectiveidea.oauth

import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for the Android sign-in flow.
 *
 * These don't open a real browser: Espresso-Intents intercepts the external launch and returns a
 * stubbed [ActivityResult]. `authTabSupported` is forced per-test (it's normally set by an async
 * service binding whose result depends on the installed browser) so both branches — the Auth Tab
 * path and the Custom Tab fallback — run deterministically.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPKCEFlowTest {
    private val signInUrl = "https://www.example.com/path/oauth/authorize?client_id=abc"
    private val redirectUrl = "exampleapp://oauth"

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun authTabSuccessfulRedirectDeliversCallbackUrl() {
        val callbackUrl = "exampleapp://oauth?code=the-auth-code"

        // On RESULT_OK the Auth Tab contract reads the callback URL from the result Intent's data.
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_OK, Intent().apply { data = Uri.parse(callbackUrl) }),
        )

        val (url, error) = runAuthTabSignIn()

        assertEquals(callbackUrl, url)
        assertNull(error)

        // We launched an Auth Tab for the sign-in URL, watching for the redirect scheme.
        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(Uri.parse(signInUrl)),
                hasExtra(AuthTabIntent.EXTRA_REDIRECT_SCHEME, "exampleapp"),
            ),
        )
    }

    @Test
    fun authTabCanceledResultReportsCancellation() {
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_CANCELED, null),
        )

        val (url, error) = runAuthTabSignIn()

        assertNull(url)
        assertEquals("Sign in was canceled.", error)
    }

    @Test
    fun unsupportedBrowserFallsBackToACustomTab() {
        // Intercept the external launch so no real browser opens.
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_CANCELED, null),
        )

        var handlerInvoked = false
        ActivityScenario.launch(AuthTabTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.flow.authTabSupportedOverride = false
                activity.flow.startSignIn(signInUrl, redirectUrl) { _, _ -> handlerInvoked = true }
            }
        }

        // A plain Custom Tab (ACTION_VIEW for the sign-in URL), not an Auth Tab.
        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasData(Uri.parse(signInUrl)),
                not(hasExtra(AuthTabIntent.EXTRA_LAUNCH_AUTH_TAB, true)),
            ),
        )
        // The Custom Tab fallback delivers its result via onNewIntent, not the completion handler.
        assertFalse("completion handler should not fire for the Custom Tab fallback", handlerInvoked)
    }

    /**
     * Forces the Auth Tab branch, launches the test host, kicks off [AndroidPKCEFlow.startSignIn],
     * and returns the (callbackUrl, errorMessage) delivered to the completion handler once the
     * stubbed Auth Tab result is dispatched back to the launcher.
     */
    private fun runAuthTabSignIn(): Pair<String?, String?> {
        val latch = CountDownLatch(1)
        var url: String? = null
        var error: String? = null

        ActivityScenario.launch(AuthTabTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.flow.authTabSupportedOverride = true
                activity.flow.startSignIn(signInUrl, redirectUrl) { callbackUrl, errorMessage ->
                    url = callbackUrl
                    error = errorMessage
                    latch.countDown()
                }
            }
            assertTrue("completion handler was not invoked", latch.await(5, TimeUnit.SECONDS))
        }

        return url to error
    }
}
