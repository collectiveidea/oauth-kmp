package com.collectiveidea.oauth

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Test host for [CurrentActivityWebAuthSession]. Binds the (test-provided) [holder] from `onCreate`,
 * exactly as a real Activity would, so tests can exercise binding and survive-recreation behavior.
 */
class HolderTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreateCount++
        holder.bindTo(this)
    }

    companion object {
        lateinit var holder: CurrentActivityWebAuthSession

        /** Incremented on every `onCreate`, so tests can confirm a recreation actually happened. */
        @Volatile
        var onCreateCount = 0
    }
}
