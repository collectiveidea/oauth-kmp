package com.collectiveidea.oauth

import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
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
 * stubbed [ActivityResult]. Most tests set `authTabSupportedOverride` to force a branch (Auth Tab
 * vs. Custom Tab fallback) deterministically; [withoutOverrideBranchesOnRealAuthTabSupport] leaves
 * it unset to exercise the real `CustomTabsClient.isAuthTabSupported()` detection.
 *
 * Note: `intended(...)` is always asserted inside the `ActivityScenario.use { }` block, i.e. while
 * the host activity is still RESUMED — Espresso-Intents needs a resumed activity to run the check.
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

        val (url, error) = runAuthTabSignIn {
            // We launched an Auth Tab for the sign-in URL, watching for the redirect scheme.
            intended(
                allOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData(Uri.parse(signInUrl)),
                    hasExtra(AuthTabIntent.EXTRA_REDIRECT_SCHEME, "exampleapp"),
                ),
            )
        }

        assertEquals(callbackUrl, url)
        assertNull(error)
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
    fun authTabUnexpectedResultReportsFailure() {
        // A non-OK, non-CANCELED result (e.g. an HTTPS-redirect verification failure) is an error.
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_VERIFICATION_FAILED, null),
        )

        val (url, error) = runAuthTabSignIn()

        assertNull(url)
        assertEquals("Sign in failed (result code ${AuthTabIntent.RESULT_VERIFICATION_FAILED}).", error)
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

            // A plain Custom Tab (ACTION_VIEW for the sign-in URL), not an Auth Tab.
            intended(
                allOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData(Uri.parse(signInUrl)),
                    not(hasExtra(AuthTabIntent.EXTRA_LAUNCH_AUTH_TAB, true)),
                ),
            )
        }

        // The Custom Tab fallback delivers its result via onNewIntent, not the completion handler.
        assertFalse("completion handler should not fire for the Custom Tab fallback", handlerInvoked)
    }

    @Test
    fun withoutOverrideBranchesOnRealAuthTabSupport() {
        // No override here: exercise the real CustomTabsClient.isAuthTabSupported() detection and
        // assert we launch an Auth Tab iff the installed browser actually supports it.
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_CANCELED, null),
        )

        ActivityScenario.launch(AuthTabTestActivity::class.java).use { scenario ->
            var authTabSupported = false
            scenario.onActivity { activity ->
                val browserPackage = CustomTabsClient.getPackageName(activity, emptyList())
                authTabSupported =
                    browserPackage != null && CustomTabsClient.isAuthTabSupported(activity, browserPackage)
                activity.flow.startSignIn(signInUrl, redirectUrl) { _, _ -> }
            }

            val launchesAuthTab = hasExtra(AuthTabIntent.EXTRA_LAUNCH_AUTH_TAB, true)
            intended(
                allOf(
                    hasAction(Intent.ACTION_VIEW),
                    hasData(Uri.parse(signInUrl)),
                    if (authTabSupported) launchesAuthTab else not(launchesAuthTab),
                ),
            )
        }
    }

    @Test
    fun recreatedResultHandlerIsNotUsedWhileASignInIsInFlight() {
        // With an in-flight startSignIn handler present, the Auth Tab result is delivered there and
        // the completionHandlerAfterRecreate fallback (for results redelivered to a reconstructed flow) is left
        // untouched.
        AuthTabTestActivity.recreatedResult = null

        val callbackUrl = "exampleapp://oauth?code=the-auth-code"
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_OK, Intent().apply { data = Uri.parse(callbackUrl) }),
        )

        val (url, error) = runAuthTabSignIn()

        assertEquals(callbackUrl, url)
        assertNull(error)
        assertNull(
            "completionHandlerAfterRecreate must not fire while startSignIn's handler is active",
            AuthTabTestActivity.recreatedResult,
        )
    }

    @Test
    fun recreatedResultIsDeliveredToCompletionHandlerAfterRecreate() {
        // A result redelivered to a flow reconstructed after the sign-in launched (e.g. across a
        // rotation) arrives with no in-flight startSignIn handler, so it must reach completionHandlerAfterRecreate.
        // The real Activity Result redelivery only happens across an actual recreation, so drive the
        // launcher's delivery path directly rather than through startSignIn.
        AuthTabTestActivity.recreatedResult = null
        val callbackUrl = "exampleapp://oauth?code=the-auth-code"

        ActivityScenario.launch(AuthTabTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.flow.deliverAuthTabResult(AuthTabIntent.RESULT_OK, callbackUrl)
            }
        }

        assertEquals(callbackUrl to null, AuthTabTestActivity.recreatedResult)
    }

    /**
     * Forces the Auth Tab branch, launches the test host, kicks off [AndroidPKCEFlow.startSignIn],
     * runs [verifyLaunch] while the activity is still resumed, and returns the (callbackUrl,
     * errorMessage) delivered to the completion handler once the stubbed Auth Tab result is
     * dispatched back to the launcher.
     */
    private fun runAuthTabSignIn(verifyLaunch: () -> Unit = {}): Pair<String?, String?> {
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

            verifyLaunch()
            assertTrue("completion handler was not invoked", latch.await(5, TimeUnit.SECONDS))
        }

        return url to error
    }
}
