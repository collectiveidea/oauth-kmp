package com.collectiveidea.oauth

import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.browser.auth.AuthTabIntent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for [CurrentActivityWebAuthSession] — the app-scoped holder that forwards to
 * the current Activity's [AndroidWebAuthSession].
 *
 * [HolderTestActivity] calls `holder.bindTo(this)` from `onCreate`; each test supplies a fresh holder
 * (with its completion handler captured via [CurrentActivityWebAuthSession.factory], as [PKCEFlow]
 * would) before launching.
 */
@RunWith(AndroidJUnit4::class)
class CurrentActivityWebAuthSessionTest {
    private val signInUrl = "https://www.example.com/path/oauth/authorize?client_id=abc"
    private val redirectUrl = "exampleapp://oauth"

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun startSignInWithoutABoundActivityFails() {
        val session = CurrentActivityWebAuthSession()
        session.factory.create { _, _ -> } // capture the handler, as PKCEFlow would

        assertThrows(IllegalArgumentException::class.java) {
            session.startSignIn(signInUrl, redirectUrl)
        }
    }

    @Test
    fun bindToForwardsStartSignInToTheActivitysSession() {
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_CANCELED, null),
        )
        HolderTestActivity.holder = CurrentActivityWebAuthSession().also { it.factory.create { _, _ -> } }

        ActivityScenario.launch(HolderTestActivity::class.java).use { scenario ->
            scenario.onActivity { HolderTestActivity.holder.startSignIn(signInUrl, redirectUrl) }

            // Forwarded to the bound Activity's AndroidWebAuthSession, which launched the browser.
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(Uri.parse(signInUrl))))
        }
    }

    @Test
    fun bindingSurvivesActivityRecreation() {
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_CANCELED, null),
        )
        HolderTestActivity.onCreateCount = 0
        HolderTestActivity.holder = CurrentActivityWebAuthSession().also { it.factory.create { _, _ -> } }

        ActivityScenario.launch(HolderTestActivity::class.java).use { scenario ->
            scenario.recreate()

            // The Activity really was recreated — a fresh instance ran onCreate and rebound.
            assertEquals(2, HolderTestActivity.onCreateCount)

            // On a relaunch the old instance's onDestroy runs first — releasing its binding — and
            // the recreated instance's onCreate rebinds immediately after, before any redelivered
            // result could be dispatched, so startSignIn still forwards.
            scenario.onActivity { HolderTestActivity.holder.startSignIn(signInUrl, redirectUrl) }
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(Uri.parse(signInUrl))))
        }
    }

    @Test
    fun finishingABoundActivityHandsTheBindingBackToTheOneBehindIt() {
        intending(not(isInternal())).respondWith(
            ActivityResult(AuthTabIntent.RESULT_CANCELED, null),
        )
        HolderTestActivity.holder = CurrentActivityWebAuthSession().also { it.factory.create { _, _ -> } }
        SecondHolderTestActivity.resumedLatch = CountDownLatch(1)
        SecondHolderTestActivity.destroyedLatch = CountDownLatch(1)

        ActivityScenario.launch(HolderTestActivity::class.java).use { scenario ->
            // A second bound Activity launched on top takes over the binding, as in a
            // multi-Activity app.
            scenario.onActivity { it.startActivity(Intent(it, SecondHolderTestActivity::class.java)) }
            assertTrue(
                "second Activity did not resume",
                SecondHolderTestActivity.resumedLatch.await(5, TimeUnit.SECONDS),
            )

            // Finish it (as back navigation would) and wait out its onDestroy, which runs after
            // the first Activity has already resumed — and reclaimed the binding.
            getInstrumentation().runOnMainSync { SecondHolderTestActivity.instance!!.finish() }
            assertTrue(
                "second Activity was not destroyed",
                SecondHolderTestActivity.destroyedLatch.await(5, TimeUnit.SECONDS),
            )

            // The first Activity owns the binding again, so startSignIn forwards to its session
            // instead of failing on the finished Activity's released one.
            scenario.onActivity { HolderTestActivity.holder.startSignIn(signInUrl, redirectUrl) }
            intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(Uri.parse(signInUrl))))
        }
    }

    @Test
    fun theSessionIsReleasedWhenItsActivityIsFinished() {
        HolderTestActivity.holder = CurrentActivityWebAuthSession().also { it.factory.create { _, _ -> } }

        // Truly finishing the Activity (not a recreation) must release the bound session — the case
        // the guard has to tell apart from a recreation, where the session survives.
        ActivityScenario.launch(HolderTestActivity::class.java).close()

        // Nothing is bound, so startSignIn has no session to forward to. Run it on the main thread
        // (as production does) so the release performed there is observed.
        var released = false
        getInstrumentation().runOnMainSync {
            released = runCatching {
                HolderTestActivity.holder.startSignIn(signInUrl, redirectUrl)
            }.exceptionOrNull() is IllegalArgumentException
        }
        assertTrue("session should be released after its Activity is finished", released)
    }
}
