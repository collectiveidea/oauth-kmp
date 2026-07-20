package com.collectiveidea.oauth

public interface PlatformPKCEFlow {
    /**
     * Starts the PKCE OAuth flow in a platform-native external browser session.
     *
     * For [com.collectiveidea.oauth.AndroidPKCEFlow], this uses a Chrome Auth Tab when the browser
     *  supports it and otherwise falls back to a Custom Tab.
     * For [com.collectiveidea.oauth.IosPKCEFlow], this is done via AuthenticationServices.
     *
     * @param signInUrl The result of a call to [PKCEFlow.buildSignInUrl]
     * @param redirectUrl The app scheme oauth link that the external browser should redirect the
     *  user to after successful authorization (to transfer control back to the app), e.g. "exampleapp://oauth".
     *  The server will append a "?code=example_auth_code" to this Url for the app to process.
     * @param completionHandler A reference to the [PKCEFlow.continueSignInWithCallbackOrError] method.
     *  iOS — and Android when the browser supports Auth Tab — invoke this completion handler
     *  directly with the callbackUrl (or an error message), so the app needs no `onNewIntent`
     *  handling. On older Android devices/browsers that fall back to a Custom Tab, the app must
     *  instead call it from `onNewIntent` (see [com.collectiveidea.oauth.AndroidPKCEFlow]).
     */
    public fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
        completionHandler: (String?, String?) -> Unit,
    )
}
