# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
  handler; `AndroidWebAuthSession(activity, completionHandler)` and `IosWebAuthSession(completionHandler)` take it
  in their constructors; and `PKCEFlow` now takes a `WebAuthSessionFactory` (a `fun interface`, so a
  lambda or a constructor reference like `::IosWebAuthSession` still works, but it can also be
  provided by type from a DI graph) that it calls with its own `continueSignInWithCallbackOrError`. On Android that single handler is what lets an Auth Tab sign-in
  interrupted by an Activity recreation (e.g. a rotation) complete when its result is redelivered to
  the rebuilt flow, instead of being dropped and forcing the user to start over — it replaces the
  earlier `completionHandlerAfterRecreate` parameter. If the sign-in is lost entirely (e.g. process
  death clears the in-memory PKCE verifier), the flow finishes with a clear "try again" error rather
  than a confusing internal one.

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
