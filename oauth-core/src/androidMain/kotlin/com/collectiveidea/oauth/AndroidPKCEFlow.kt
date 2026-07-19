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
 */
public class AndroidPKCEFlow(
    private val activity: ComponentActivity,
) : PlatformPKCEFlow {
    private var completionHandler: ((String?, String?) -> Unit)? = null

    // Registered eagerly (at construction) so it is in place before the host is STARTED.
    private val authTabLauncher: ActivityResultLauncher<Intent> =
        AuthTabIntent.registerActivityResultLauncher(activity) { result ->
            val handler = completionHandler
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
        this.completionHandler = completionHandler

        if (isAuthTabSupported()) {
            // The Auth Tab watches for this scheme (e.g. "exampleapp" from "exampleapp://oauth")
            // and returns the redirect to authTabLauncher's callback.
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
