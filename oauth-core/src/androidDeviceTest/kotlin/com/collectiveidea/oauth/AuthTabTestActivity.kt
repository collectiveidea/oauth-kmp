package com.collectiveidea.oauth

import androidx.activity.ComponentActivity
import java.util.concurrent.CountDownLatch

/**
 * Test host that constructs [AndroidWebAuthSession] as a field — during activity construction, before
 * the lifecycle reaches STARTED — which is what the Auth Tab launcher registration requires.
 * Tests then drive the already-registered launcher via `scenario.onActivity { it.flow... }`.
 *
 * The flow's single completion handler records what it receives into [result] and releases
 * [resultLatch], so tests can await it and assert the delivered (callbackUrl, errorMessage).
 */
class AuthTabTestActivity : ComponentActivity() {
    val flow = AndroidWebAuthSession(this) { callbackUrl, errorMessage ->
        result = callbackUrl to errorMessage
        resultLatch.countDown()
    }

    companion object {
        /** The last result the flow reported. Reset it in tests before use. */
        @Volatile
        var result: Pair<String?, String?>? = null

        /** Released when the flow reports a result. Reset it in tests before awaiting. */
        @Volatile
        var resultLatch = CountDownLatch(1)
    }
}
