package com.collectiveidea.oauth

import androidx.activity.ComponentActivity

/**
 * Test host that constructs [AndroidPKCEFlow] as a field — during activity construction, before
 * the lifecycle reaches STARTED — which is what the Auth Tab launcher registration requires.
 * Tests then drive the already-registered launcher via `scenario.onActivity { it.flow... }`.
 */
class AuthTabTestActivity : ComponentActivity() {
    val flow = AndroidPKCEFlow(this)
}
