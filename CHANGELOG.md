# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

* Update dependencies. See [#18](https://github.com/collectiveidea/oauth-kmp/pull/18).
  * Kotlin to [2.4.21](https://kotlinlang.org/docs/whatsnew2420.html)
  * kotlinx-serialization to [1.13.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.13.0)
  * Ktor to [3.6.1](https://github.com/ktorio/ktor/releases/tag/3.6.1)
  * Okio to [3.18.2](https://github.com/square/okio/blob/master/CHANGELOG.md#version-3182)
  * AGP to [9.5.2](https://developer.android.com/build/releases/agp-9-5-0-release-notes) / Gradle to [9.9.2](https://docs.gradle.org/9.9.2/release-notes.html)
  * kotlinx-coroutines (1.11.0), androidx-activity (1.13.0), androidx-browser (1.10.0) and krypt (0.3.1) were already on their latest releases.
* Throw every error response from an `installJsonOAuth` client as an `OAuthException`. A JSON error body that doesn't decode as an `OAuthError` (a framework's generic error JSON, or truncated JSON) used to escape as Ktor's `JsonConvertException`; it now gets the code `unknown` and the raw body as its description, as a non-JSON body already did. See [#19](https://github.com/collectiveidea/oauth-kmp/pull/19).
* Ignore fields this library doesn't model in token and error responses, as [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749#section-5.1) requires of a client. A token response carrying an extra field (an OpenID Connect `id_token`, say) used to fail the sign-in or refresh with Ktor's `JsonConvertException`, and an error carrying an `error_uri` lost its code to `unknown`. See [#19](https://github.com/collectiveidea/oauth-kmp/pull/19).
* **Breaking:** `OAuthError.description` (`description_` in Swift) is now a nullable `String?` that defaults to null, since [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749#section-5.2) makes `error_description` optional. A bare `{"error":"invalid_grant"}` now decodes with its code instead of falling back to `unknown`, and an `OAuthException` without a description takes the code as its message, so `PKCEFlow`'s `errorMessage` is never null on a failed token exchange. See [#19](https://github.com/collectiveidea/oauth-kmp/pull/19).
* **Breaking:** `TokenResponse.expiresIn` is now a nullable `Int?` (`KotlinInt?` in Swift) and `TokenResponse.createdAt` a nullable `Long?` (`KotlinLong?` in Swift), both defaulting to null, so token responses from servers other than Doorkeeper decode: [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749#section-5.1) only recommends `expires_in`, and `created_at` is a Doorkeeper extension. `createdAt` is a `Long` so it holds issue times past January 2038, which overflow an `Int`. See [#19](https://github.com/collectiveidea/oauth-kmp/pull/19).
* Encode the whole redirect URL into the sign-in URL. A redirect URL with its own query string (`exampleapp://oauth?source=app&step=2`) was cut off at its first `&`. See [#20](https://github.com/collectiveidea/oauth-kmp/pull/20).
* **Breaking:** `PKCEFlow` takes the scopes to request as `scopes: List<String>` instead of always requesting `public write`. It defaults to none, which omits `scope` from the authorization request so the server applies its default scopes ([RFC 6749 §3.3](https://datatracker.ietf.org/doc/html/rfc6749#section-3.3)). To keep requesting what earlier versions did, pass `listOf("public", "write")`; Swift callers pass `scopes` explicitly, since Kotlin default arguments don't reach Swift. See [#20](https://github.com/collectiveidea/oauth-kmp/pull/20).

## [0.3.0] - 2026-07-21

BREAKING API Changes and Additions. See [#15](https://github.com/collectiveidea/oauth-kmp/pull/15).

* Add `CurrentActivityWebAuthSession` (Android): an app-scoped `WebAuthSession` for the common setup
  where `PKCEFlow` is a singleton but `AndroidWebAuthSession` must be rebuilt per `Activity`. Hand its
  `factory` to `PKCEFlow` and call `bindTo(activity)` from `onCreate`; it forwards to the current
  `Activity`'s session, hands the binding back to the previous bound `Activity` when one is
  finished, and releases it once no bound `Activity` remains. A recreated `Activity` rebinds in
  `onCreate`, so an in-flight sign-in survives recreation.
* **Breaking:** renamed the platform browser types so they no longer read as variants of the
  `PKCEFlow` orchestrator — they run the browser authorization step and do no PKCE themselves:
  `PlatformPKCEFlow` → `WebAuthSession`, `AndroidPKCEFlow` → `AndroidWebAuthSession`, and
  `IosPKCEFlow` → `IosWebAuthSession`.
* **Breaking:** each `WebAuthSession` now receives its completion handler once, at construction,
  instead of on every `startSignIn` call. `startSignIn(signInUrl, redirectUrl)` no longer takes a
  handler; `AndroidWebAuthSession(activity, completionHandler)` and
  `IosWebAuthSession(completionHandler)` take it in their constructors; and `PKCEFlow` now takes a
  `WebAuthSessionFactory` (a `fun interface`, so a lambda or a constructor reference like
  `::IosWebAuthSession` still works, but it can also be provided by type from a DI graph) that it
  calls with its own `continueSignInWithCallbackOrError`. On Android that single handler is what
  lets an Auth Tab sign-in interrupted by an Activity recreation (e.g. a rotation) complete when
  its result is redelivered to the rebuilt session, instead of being dropped and forcing the user to
  start over. If the sign-in is lost entirely (e.g. process death clears the in-memory PKCE
  verifier), `PKCEFlow` finishes with a clear "try again" error rather than a confusing internal one.
* **Breaking:** `PKCEFlow`'s `externalScope` constructor parameter is renamed `applicationScope`, to
  signal it should be an app-lifetime scope — it launches the token exchange, which shouldn't be
  cancelled by UI teardown.
* Add a `WebAuthSessionCompletionHandler` typealias (`(callbackUrl: String?, errorMessage: String?) ->
  Unit`) naming the handler a `WebAuthSession` reports its result (the redirect URL, or an error) to.

## [0.2.0] - 2026-07-20

* Bump ktlint to 1.8.0. See [#10](https://github.com/collectiveidea/oauth-kmp/pull/10).
* Update dependencies. See [#11](https://github.com/collectiveidea/oauth-kmp/pull/11).
  * Kotlin to 2.4.10
  * AGP to 9.3.0 / Gradle to 9.6.1
  * Ktor to 3.5.1
  * kotlinx-serialization to 1.11.0
  * kotlinx-coroutines to 1.11.0
  * okio to 3.17.0
  * androidx-browser to 1.10.0
  * Adopt the Android Kotlin Multiplatform library plugin. The published library now
    emits Java 11 bytecode (build against it with a JDK 11+ toolchain) and its Android
    artifact is a single variant. The minimum iOS deployment target is now 15 (Kotlin 2.4).
* Use a Chrome [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab) for the
  Android sign-in flow when the browser supports it (Chrome 137+), falling back to a Custom Tab
  otherwise. On the Auth Tab path the redirect is delivered directly to the app, so `onNewIntent`
  handling is only needed for the Custom Tab fallback. **Breaking:** `AndroidPKCEFlow` now takes a
  `ComponentActivity` instead of a `Context`. See [#12](https://github.com/collectiveidea/oauth-kmp/pull/12).

## [0.1.2] - 2025-10-24

* Update dependencies. See [#7](https://github.com/collectiveidea/oauth-kmp/pull/7).
  * Kotlin to 2.2.20
  * AGP to 8.13 / Gradle to 8.14.3
  * Ktor to 3.3.1
  * kotlinx-serialization to 1.9.0
  * androidx-browser to 1.9.0
  * kotlinx-coroutines to 1.10.2
  * okio to 3.16.2
  * Compile Android with SDK 36

## [0.1.1] - 2024-11-26

 * Update dependencies. See [#5](https://github.com/collectiveidea/oauth-kmp/pull/5).
   * ktor to 3.0.1
   * okio to 3.9.1
   * kotlinx-serialization to 1.7.3
   * kotlinx-coroutines to 1.9.0
   * Kotlin to 2.0.21

## [0.1.0] - 2024-09-06

Initial Release
