package com.collectiveidea.oauth

import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * App-scoped [WebAuthSession] for the common Android setup where a single [PKCEFlow] outlives the
 * host Activity (e.g. it's a DI singleton) but [AndroidWebAuthSession] must be rebuilt for each
 * Activity — it registers an Activity Result launcher, which has to happen before the Activity is
 * STARTED. This holds the current Activity's [AndroidWebAuthSession] and forwards to it.
 *
 * Wire it up once, as an app singleton, handing its [factory] to [PKCEFlow]:
 * ```
 * val session = CurrentActivityWebAuthSession()
 * val pkceFlow = PKCEFlow(webAuthSessionFactory = session.factory, /* ... */)
 * ```
 * then bind each Activity from its `onCreate`, before it is STARTED:
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     session.bindTo(this)
 *     // ...
 * }
 * ```
 *
 * A lifecycle observer keeps the binding on the foreground Activity: a bound Activity reclaims it
 * whenever it resumes (so finishing one bound Activity hands the binding back to the one behind
 * it), and a destroyed Activity releases it only if it still owns it. An in-flight Auth Tab sign-in
 * therefore survives a recreation: the old instance's `onDestroy` releases the binding, and the
 * recreated Activity rebinds in its `onCreate` immediately after — before the redelivered result
 * can be dispatched.
 */
public class CurrentActivityWebAuthSession : WebAuthSession {
    private var completionHandler: ((String?, String?) -> Unit)? = null
    private var delegate: AndroidWebAuthSession? = null

    /**
     * Hand this to [PKCEFlow] as its `webAuthSessionFactory`. [PKCEFlow] calls it once, at
     * construction, so this can capture the completion handler that each per-Activity
     * [AndroidWebAuthSession] should report results to.
     */
    public val factory: WebAuthSessionFactory =
        WebAuthSessionFactory { handler ->
            completionHandler = handler
            this
        }

    /**
     * Builds and binds the [AndroidWebAuthSession] for [activity]. Call from the Activity's
     * `onCreate`, before it is STARTED. The [PKCEFlow] built with [factory] must already exist, so
     * that the completion handler has been captured.
     */
    public fun bindTo(activity: ComponentActivity) {
        val handler = requireNotNull(completionHandler) {
            "Construct the PKCEFlow that uses this session's factory before calling bindTo()."
        }
        val session = AndroidWebAuthSession(activity, handler)
        delegate = session
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    // Reclaim the binding on every return to the foreground: another bound
                    // Activity may have taken it over and since been finished.
                    delegate = session
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    // Release only if this session still owns the binding, so a destroyed Activity
                    // can't yank it from one that has since bound or reclaimed it.
                    if (delegate === session) {
                        delegate = null
                    }
                }
            },
        )
    }

    override fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
    ) {
        val delegate = requireNotNull(delegate) {
            "No Activity is bound. Call bindTo(activity) from the host Activity's onCreate."
        }
        delegate.startSignIn(signInUrl, redirectUrl)
    }
}
