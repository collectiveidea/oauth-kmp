package com.collectiveidea.oauth

public interface PlatformPKCEFlow {
    /**
     * Starts the PKCE OAuth flow in a platform-native external browser session.
     *
     * For [com.collectiveidea.oauth.AndroidPKCEFlow], this is done via Custom Tabs.
     * For [com.collectiveidea.oauth.IosPKCEFlow], this is done via AuthenticationServices.
     *
     * @param signInUrl The result of a call to [PKCEFlow.buildSignInUrl]
     * @param redirectUrl The app scheme oauth link that the external browser should redirect the
     *  user to after successful authorization (to transfer control back to the app), e.g. "exampleapp://oauth".
     *  The server will append a "?code=example_auth_code" to this Url for the app to process.
     * @param completionHandler A reference to the [PKCEFlow.continueSignInWithCallbackOrError] method.
     *  When supported by the platform (e.g. iOS), the sign in process may directly invoke this completion
     *  handler with the callbackUrl, without the app having to transfer control back to the [PKCEFlow] explicitly.
     *
     *  Android will not invoke the completionHandler; It must be called in `onNewIntent` once
     *  the platform transfer control back to the app Activity via the redirectUrl.
     */
    public fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
        completionHandler: (String?, String?) -> Unit,
    )
}
