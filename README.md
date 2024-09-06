[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081)](https://pinterest.github.io/ktlint/)

# OAuth PKCE Flow for Kotlin Multiplatform (Android/iOS)

A Kotlin Multiplatform library for Android and iOS that provides an [OAuth PKCE flow](https://oauth.net/2/pkce/) implementation.

# Installation

⚠️ This is a work-in-progress library that was recently extracted from
a reference project. The API is subject to change. Detailed installation
instructions need to be written.

In general, the key steps for using this library are:

 * Configure your iOS and Android apps to have a custom url app scheme (e.g. `exampleapp://`)
 * Create an iOS and Android OAuth client on your server (we use [Doorkeeper](https://github.com/doorkeeper-gem/doorkeeper)) for the PKCE flow and Redirect URI. Configure your iOS and Android apps with the proper `client_id` values.
 * In your shared view model, create/inject a [`PKCEFlow`](https://github.com/collectiveidea/oauth-kmp/blob/main/oauth-core/src/commonMain/kotlin/com/collectiveidea/oauth/PKCEFlow.kt).
 * Collect the [`authState`](https://github.com/collectiveidea/oauth-kmp/blob/main/oauth-core/src/commonMain/kotlin/com/collectiveidea/oauth/PKCEFlow.kt#L60C16-L60C25) to be notified when the sign in process completes.
 * Call [`startSignIn`](https://github.com/collectiveidea/oauth-kmp/blob/main/oauth-core/src/commonMain/kotlin/com/collectiveidea/oauth/PKCEFlow.kt#L100C16-L100C27) 
   * On Android, in your main activity, override `onNewIntent` and handle the auth callback. Something like:
    ```kotlin
    override fun onNewIntent(intent: Intent) {
        // Ensure the callbackUrl is for OAuth before processing it as such.
        if (intent.data?.toString()?.startsWith(PKCE_REDIRECT_URL) == true) {
            pkceFlow.continueSignInWithCallbackOrError(dataUrl, null)
        }
    }
    ```
   * On iOS, nothing else is needed. The [callback is automatically invoked](https://github.com/collectiveidea/oauth-kmp/blob/main/oauth-core/src/iosMain/kotlin/com/collectiveidea/oauth/IosPKCEFlow.kt#L26) by [`AuthenticationServices`](https://developer.apple.com/documentation/authenticationservices).
 * When the [`authState.state`](https://github.com/collectiveidea/oauth-kmp/blob/main/oauth-core/src/commonMain/kotlin/com/collectiveidea/oauth/PKCEFlow.kt#L47) is `FINISHED`, either extract/save the tokens and proceed with sign-in, or present the user with an error message. See, e.g. https://github.com/collectiveidea/oauth-kmp/blob/main/oauth-core/src/commonTest/kotlin/com/collectiveidea/oauth/PKCEFlowTest.kt#L186
 
