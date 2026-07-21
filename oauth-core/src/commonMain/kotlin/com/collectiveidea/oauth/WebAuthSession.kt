package com.collectiveidea.oauth

public interface WebAuthSession {
    /**
     * Starts the PKCE OAuth flow in a platform-native external browser session.
     *
     * Not an app entry point: [PKCEFlow.startSignIn] is what calls this, after generating the PKCE
     * verifier and building [signInUrl] (via the `internal` [PKCEFlow.buildSignInUrl]). App code
     * always starts a sign-in with [PKCEFlow.startSignIn] — a session started here directly has no
     * verifier behind it, so the callback it reports could never be exchanged for tokens.
     *
     * For [com.collectiveidea.oauth.AndroidWebAuthSession], this uses a Chrome Auth Tab when the browser
     *  supports it and otherwise falls back to a Custom Tab.
     * For [com.collectiveidea.oauth.IosWebAuthSession], this is done via AuthenticationServices.
     *
     * The result (the callback URL, or an error message) is reported to the completion handler the
     * implementation was constructed with — a reference to [PKCEFlow.continueSignInWithCallbackOrError]
     * that [PKCEFlow] supplies. iOS — and Android when the browser supports Auth Tab — report it
     * directly, so the app needs no `onNewIntent` handling. On older Android devices/browsers that
     * fall back to a Custom Tab, the app must instead call that method from `onNewIntent` (see
     * [com.collectiveidea.oauth.AndroidWebAuthSession]).
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

/**
 * Receives the result of a [WebAuthSession]: the OAuth redirect `callbackUrl` on success, or an
 * `errorMessage` on failure — exactly one is non-null. Typically a reference to
 * [PKCEFlow.continueSignInWithCallbackOrError].
 */
public typealias WebAuthSessionCompletionHandler = (callbackUrl: String?, errorMessage: String?) -> Unit

/**
 * Builds a [WebAuthSession] for the completion handler it should report results to. [PKCEFlow] calls
 * this with its own [PKCEFlow.continueSignInWithCallbackOrError] when it is constructed.
 *
 * It's a named `fun interface` rather than a bare function type so it can be provided by type — e.g.
 * registered per platform in a DI graph and resolved into the shared [PKCEFlow] construction. A
 * lambda or a constructor reference (e.g. `::IosWebAuthSession`) converts to it automatically.
 */
public fun interface WebAuthSessionFactory {
    public fun create(completionHandler: WebAuthSessionCompletionHandler): WebAuthSession
}
