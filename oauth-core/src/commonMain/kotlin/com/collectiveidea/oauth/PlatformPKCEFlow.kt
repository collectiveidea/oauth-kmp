package com.collectiveidea.oauth

public interface PlatformPKCEFlow {
    /**
     * Starts the PKCE OAuth flow in a platform-native external browser session.
     *
     * For [com.collectiveidea.oauth.AndroidPKCEFlow], this uses a Chrome Auth Tab when the browser
     *  supports it and otherwise falls back to a Custom Tab.
     * For [com.collectiveidea.oauth.IosPKCEFlow], this is done via AuthenticationServices.
     *
     * The result (the callback URL, or an error message) is reported to the completion handler the
     * implementation was constructed with — a reference to [PKCEFlow.continueSignInWithCallbackOrError]
     * that [PKCEFlow] supplies. iOS — and Android when the browser supports Auth Tab — report it
     * directly, so the app needs no `onNewIntent` handling. On older Android devices/browsers that
     * fall back to a Custom Tab, the app must instead call that method from `onNewIntent` (see
     * [com.collectiveidea.oauth.AndroidPKCEFlow]).
     *
     * @param signInUrl The result of a call to [PKCEFlow.buildSignInUrl]
     * @param redirectUrl The app scheme oauth link that the external browser should redirect the
     *  user to after successful authorization (to transfer control back to the app), e.g. "exampleapp://oauth".
     *  The server will append a "?code=example_auth_code" to this Url for the app to process.
     */
    public fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
    )
}
