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
 * API 26 and up), sign-in uses an Auth Tab: it captures the OAuth redirect itself and returns the
 * callback URL directly to [startSignIn]'s `completionHandler`, exactly like iOS — no
 * redirect-scheme `<intent-filter>` or `onNewIntent` handling required.
 *
 * On older devices/browsers without Auth Tab support (e.g. API 23–25, which cannot install Chrome
 * 137+), it falls back to a plain Custom Tab. In that case the redirect comes back the classic
 * way, so the app must still forward it: register the redirect scheme with an `<intent-filter>`
 * and, from `onNewIntent`, call [PKCEFlow.continueSignInWithCallbackOrError].
 *
 * Because it registers an Activity Result launcher, [activity] must be constructed as a field or
 * early in `onCreate` (before the activity reaches STARTED), per the Activity Result API contract.
 * A new instance is therefore created for each Activity instance; pass [onRecreatedResult] so an
 * Auth Tab result that is redelivered to a freshly-recreated instance (e.g. after a rotation mid
 * sign-in) is still delivered instead of dropped.
 *
 * A full process death still loses an in-flight sign-in: the recreated flow's PKCE verifier is gone,
 * so a redelivered callback URL can no longer be exchanged and the user must restart sign-in.
 *
 * @param activity the host Activity, used to register the Auth Tab launcher and launch the browser.
 * @param onRecreatedResult optional handler — typically `PKCEFlow::continueSignInWithCallbackOrError`
 *  — invoked with an Auth Tab result that is redelivered after this flow was reconstructed, i.e. when
 *  no [startSignIn] call is in flight to receive it. When `null` (the default) such a result is
 *  dropped and the user must restart sign-in.
 */
public class AndroidPKCEFlow(
    private val activity: ComponentActivity,
    private val onRecreatedResult: ((String?, String?) -> Unit)? = null,
) : PlatformPKCEFlow {
    private var completionHandler: ((String?, String?) -> Unit)? = null

    // Registered eagerly (at construction) so it is in place before the host is STARTED.
    private val authTabLauncher: ActivityResultLauncher<Intent> =
        AuthTabIntent.registerActivityResultLauncher(activity) { result ->
            // Prefer the in-flight handler from startSignIn; fall back to onRecreatedResult when the
            // result is redelivered to a flow reconstructed after the sign-in was launched.
            val handler = completionHandler ?: onRecreatedResult
            completionHandler = null

            when (result.resultCode) {
                AuthTabIntent.RESULT_OK -> handler?.invoke(result.resultUri?.toString(), null)
                AuthTabIntent.RESULT_CANCELED -> handler?.invoke(null, "Sign in was canceled.")
                else -> handler?.invoke(null, "Sign in failed (result code ${result.resultCode}).")
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
        completionHandler: (String?, String?) -> Unit,
    ) {
        if (isAuthTabSupported()) {
            // The Auth Tab returns its result to authTabLauncher's callback, which forwards it to
            // this handler. It watches for the redirect scheme (e.g. "exampleapp" from
            // "exampleapp://oauth") to capture the redirect.
            this.completionHandler = completionHandler
            val redirectScheme = redirectUrl.substringBefore("://")
            val authTabIntent = AuthTabIntent.Builder().build()
            authTabIntent.launch(authTabLauncher, Uri.parse(signInUrl), redirectScheme)
        } else {
            // Fallback: a plain Custom Tab. The redirect returns via the app's redirect-scheme
            // intent-filter + onNewIntent, where the app calls continueSignInWithCallbackOrError,
            // so completionHandler is not used on this path.
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
