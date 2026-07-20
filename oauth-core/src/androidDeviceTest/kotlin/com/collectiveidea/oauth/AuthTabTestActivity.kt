package com.collectiveidea.oauth

import androidx.activity.ComponentActivity

/**
 * Test host that constructs [AndroidPKCEFlow] as a field — during activity construction, before
 * the lifecycle reaches STARTED — which is what the Auth Tab launcher registration requires.
 * Tests then drive the already-registered launcher via `scenario.onActivity { it.flow... }`.
 */
class AuthTabTestActivity : ComponentActivity() {
    val flow = AndroidPKCEFlow(this, onRecreatedResult = { url, error -> recreatedResult = url to error })

    companion object {
        /**
         * Captures a result routed to `onRecreatedResult` — i.e. one delivered while no
         * [AndroidPKCEFlow.startSignIn] handler is in flight. Reset it in tests before use.
         */
        @Volatile
        var recreatedResult: Pair<String?, String?>? = null
    }
}
