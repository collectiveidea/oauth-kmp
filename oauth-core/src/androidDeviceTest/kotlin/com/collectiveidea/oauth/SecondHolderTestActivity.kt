package com.collectiveidea.oauth

import android.os.Bundle
import androidx.activity.ComponentActivity
import java.util.concurrent.CountDownLatch

/**
 * A second bound test host, launched on top of [HolderTestActivity] to exercise the binding moving
 * to a newer Activity and back. It binds the same [HolderTestActivity.holder] from `onCreate`; the
 * latches let tests await its resume and destruction, and [instance] lets them `finish()` it.
 */
class SecondHolderTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HolderTestActivity.holder.bindTo(this)
    }

    override fun onResume() {
        super.onResume()
        instance = this
        resumedLatch.countDown()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyedLatch.countDown()
    }

    companion object {
        /** The most recently resumed instance. Reset the latches in tests before launching. */
        @Volatile
        var instance: SecondHolderTestActivity? = null

        @Volatile
        var resumedLatch = CountDownLatch(1)

        @Volatile
        var destroyedLatch = CountDownLatch(1)
    }
}
