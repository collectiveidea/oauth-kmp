package com.collectiveidea.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

public class AndroidPKCEFlow(private val context: Context): PlatformPKCEFlow {
    override fun startSignIn(signInUrl: String, redirectUrl: String, completionHandler: (String?, String?) -> Unit) {
        // NOTE: Android is unable to directly invoke the completionHandler to process the
        // callback URL when the external web browser transfers control back to the app.

        val customTabsIntent = CustomTabsIntent.Builder().build()
        // TRICKY: If `Context` is an Application, not an Activity, we must
        // supply the `FLAG_ACTIVITY_NEW_TASK` here so that CustomTabs can
        // launch the URL in a new activity without error.
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, Uri.parse(signInUrl))
    }
}
