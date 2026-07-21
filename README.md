[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081)](https://ktlint.github.io/)

# OAuth PKCE Flow for Kotlin Multiplatform (Android/iOS)

A Kotlin Multiplatform library for Android and iOS that implements the [OAuth PKCE flow](https://oauth.net/2/pkce/).

> ⚠️ Work in progress. The API is still settling and may change before a 1.0.0 release.

## How it works

- **`PKCEFlow`** (shared) orchestrates the flow: it builds the sign-in URL, exchanges the returned
  authorization code for access/refresh tokens, and exposes progress as a `StateFlow`, `authState`
  (`NOT_STARTED` → `WAITING_FOR_AUTHORIZATION_CODE` → `EXCHANGING_AUTHORIZATION_CODE` → `FINISHED`).
  Keep one instance around — typically an app-wide singleton.
- A **`WebAuthSession`** opens the system browser to the sign-in URL and reports the redirect back to
  `PKCEFlow`. Each platform has its own: on Android a Chrome
  [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab) (with a Custom Tab
  fallback on older browsers), on iOS `ASWebAuthenticationSession`. You give `PKCEFlow` a
  **`WebAuthSessionFactory`** that builds the right one per platform.

## Installation

1. Register a **custom URL scheme** for your app (e.g. `exampleapp://`) on both platforms, and create
   an **OAuth client that supports PKCE** on your server (we use
   [Doorkeeper](https://github.com/doorkeeper-gem/doorkeeper)). You'll need its client id and a
   redirect URI that uses your scheme, e.g. `exampleapp://oauth`.
2. Add the dependency, plus the repository for its `krypt-csprng` transitive dependency:

   ```kotlin
   // settings.gradle.kts
   dependencyResolutionManagement {
       repositories {
           mavenCentral()
           maven("https://repo.repsy.io/mvn/chrynan/public") // krypt-csprng (transitive dependency)
       }
   }
   ```
   ```toml
   # gradle/libs.versions.toml
   [libraries]
   oauth-core = { module = "com.collectiveidea.oauth:oauth-core", version = "0.3.0" }
   ```
   ```kotlin
   // shared module build.gradle.kts
   kotlin.sourceSets.commonMain.dependencies {
       implementation(libs.oauth.core)
   }
   ```

## Setup

### Shared

Build one `PKCEFlow` and hold it as a singleton. Everything but the `WebAuthSessionFactory` and the
Ktor engine is shared; those two are supplied by each platform.

```kotlin
fun createPKCEFlow(
    webAuthSessionFactory: WebAuthSessionFactory, // platform-provided (see below)
    httpClientEngine: HttpClientEngine,           // OkHttp on Android, Darwin on iOS
    applicationScope: CoroutineScope,             // app-lifetime; launches the token exchange
): PKCEFlow {
    val baseUrl = "https://api.example.com/" // everything before "oauth/…", with a trailing slash

    val oauthService = OAuthServiceImpl(
        httpClient = HttpClient(httpClientEngine) { installJsonOAuth(baseUrl) },
        clientId = "your-oauth-client-id",
    )

    return PKCEFlow(
        webAuthSessionFactory = webAuthSessionFactory,
        oauthService = oauthService,
        oauthBaseUrl = baseUrl,
        redirectUrl = "exampleapp://oauth",
        applicationScope = applicationScope,
        ioDispatcher = Dispatchers.IO,
    )
}
```

### Android

`AndroidWebAuthSession` registers an Activity Result launcher, so it must be built by the hosting
`Activity` before it is STARTED — it can't be an app-wide singleton. Use the provided
`CurrentActivityWebAuthSession`: hold **it** as the singleton, give `PKCEFlow` its `factory`, and
`bindTo(...)` the current `Activity` from `onCreate`.

```kotlin
// App singletons (e.g. in your DI graph):
val webAuthSession = CurrentActivityWebAuthSession()
val pkceFlow = createPKCEFlow(
    webAuthSessionFactory = webAuthSession.factory,
    httpClientEngine = OkHttp.create(),
    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
)
```
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the Activity is STARTED. (Resolve the singletons however your app does DI.)
        webAuthSession.bindTo(this)
        // ...
    }
}
```

When the browser supports an Auth Tab (Chrome 137+, i.e. Android 8+), the redirect is captured and
delivered directly — nothing else is needed. To keep working on older devices/browsers that fall
back to a Custom Tab, register the redirect scheme and forward the redirect from `onNewIntent`:

```xml
<!-- AndroidManifest.xml -->
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="exampleapp" />
    </intent-filter>
</activity>
```
```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val url = intent.data?.toString()
    if (url != null && url.startsWith("exampleapp://oauth")) {
        pkceFlow.continueSignInWithCallbackOrError(url, null)
    }
}
```

If the `Activity` is recreated while a sign-in is in flight (e.g. under memory pressure),
`CurrentActivityWebAuthSession` rebinds the recreated `Activity` and the redelivered result still
completes — which is why `PKCEFlow` must outlive the `Activity` (hence the singleton). If the whole
process is killed the PKCE verifier is lost, so `PKCEFlow` finishes with an error and the user
restarts sign-in.

### iOS

`IosWebAuthSession`'s constructor matches `WebAuthSessionFactory`, so pass it as the factory.
`ASWebAuthenticationSession` delivers the callback directly — nothing else is needed.

```kotlin
val pkceFlow = createPKCEFlow(
    webAuthSessionFactory = ::IosWebAuthSession,
    httpClientEngine = Darwin.create(),
    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
)
```

## Driving the flow

Start sign-in (which opens the browser) and observe `authState` — for example, from a shared view
model:

```kotlin
fun signIn() = pkceFlow.startSignIn()

init {
    scope.launch {
        pkceFlow.authState.collect { state ->
            when (state.state) {
                PKCEFlow.PKCEAuthState.State.FINISHED -> {
                    val tokens = state.tokenResponse
                    if (tokens != null) {
                        // Persist the tokens and continue into the app.
                    } else {
                        // Sign-in failed — show state.errorMessage.
                    }
                    pkceFlow.resetState()
                }

                else -> {
                    // NOT_STARTED / WAITING_FOR_AUTHORIZATION_CODE / EXCHANGING_AUTHORIZATION_CODE —
                    // optionally reflect progress in the UI.
                }
            }
        }
    }
}
```
