package com.collectiveidea.oauth

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Android implementation of [PlatformPKCEFlow] that progressively enhances to a Chrome
 * [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab).
 *
 * When the installed browser supports Auth Tab (Chrome 137+, which in practice means Android 8.0 /
 * API 26 and up), sign-in uses an Auth Tab: it captures the OAuth redirect itself and reports the
 * callback URL directly to [completionHandler], exactly like iOS — no redirect-scheme
 * `<intent-filter>` or `onNewIntent` handling required.
 *
 * On older devices/browsers without Auth Tab support (e.g. API 23–25, which cannot install Chrome
 * 137+), it falls back to a plain Custom Tab. In that case the redirect comes back the classic
 * way, so the app must still forward it: register the redirect scheme with an `<intent-filter>`
 * and, from `onNewIntent`, call [PKCEFlow.continueSignInWithCallbackOrError].
 *
 * Because it registers an Activity Result launcher, [activity] must be constructed as a field or
 * early in `onCreate` (before the activity reaches STARTED), per the Activity Result API contract.
 * A new instance is therefore created for each Activity instance. [completionHandler] is supplied at
 * construction (rather than per [startSignIn] call) precisely so that an Auth Tab result redelivered
 * to a freshly-recreated instance — one that never had [startSignIn] called on it, e.g. after the
 * Activity was recreated mid sign-in — is still delivered instead of dropped. That redelivery
 * matches launchers by registration order, so the recreated Activity must register its launchers
 * (this one included) in the same order on every creation; the unconditional construction above
 * already provides that.
 *
 * A full process death still loses an in-flight sign-in: the recreated flow's PKCE verifier is gone,
 * so a redelivered callback URL can no longer be exchanged — [PKCEFlow] finishes with an error and
 * the user must restart sign-in.
 *
 * @param activity the host Activity, used to register the Auth Tab launcher and launch the browser.
 * @param completionHandler invoked with the Auth Tab result (the callback URL, or an error message);
 *  typically `PKCEFlow::continueSignInWithCallbackOrError`.
 */
public class AndroidPKCEFlow(
    private val activity: ComponentActivity,
    private val completionHandler: (String?, String?) -> Unit,
) : PlatformPKCEFlow {
    // Registered eagerly (at construction) so it is in place before the host is STARTED.
    private val authTabLauncher: ActivityResultLauncher<Intent> =
        AuthTabIntent.registerActivityResultLauncher(activity) { result ->
            deliverAuthTabResult(result.resultCode, result.resultUri?.toString())
        }

    /**
     * Routes an Auth Tab result to [completionHandler]. `internal` rather than private only so tests
     * can simulate a result redelivered to a freshly-recreated flow — the real Activity Result
     * redelivery happens only across an actual Activity recreation, which can't be staged against the
     * stubbed launcher under test.
     */
    internal fun deliverAuthTabResult(
        resultCode: Int,
        callbackUrl: String?,
    ) {
        when (resultCode) {
            AuthTabIntent.RESULT_OK -> completionHandler(callbackUrl, null)
            AuthTabIntent.RESULT_CANCELED -> completionHandler(null, "Sign in was canceled.")
            else -> completionHandler(null, "Sign in failed (result code $resultCode).")
        }
    }

    /**
     * Forces the Auth Tab vs. Custom Tab decision in tests. When `null` (production) support is
     * queried from the installed browser. `internal`, so it is not part of the public API.
     */
    internal var authTabSupportedOverride: Boolean? = null

    override fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
    ) {
        if (isAuthTabSupported()) {
            // The Auth Tab returns its result to authTabLauncher's callback, which forwards it to
            // completionHandler. It watches for the redirect scheme (e.g. "exampleapp" from
            // "exampleapp://oauth") to capture the redirect.
            val redirectScheme = redirectUrl.substringBefore("://")
            val authTabIntent = AuthTabIntent.Builder().build()
            authTabIntent.launch(authTabLauncher, Uri.parse(signInUrl), redirectScheme)
        } else {
            // Fallback: a plain Custom Tab. The redirect returns via the app's redirect-scheme
            // intent-filter + onNewIntent, where the app calls continueSignInWithCallbackOrError.
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(activity, Uri.parse(signInUrl))
        }
    }

    private fun isAuthTabSupported(): Boolean {
        authTabSupportedOverride?.let { return it }

        val browserPackage = CustomTabsClient.getPackageName(activity, emptyList()) ?: return false
        return CustomTabsClient.isAuthTabSupported(activity, browserPackage)
    }
}
