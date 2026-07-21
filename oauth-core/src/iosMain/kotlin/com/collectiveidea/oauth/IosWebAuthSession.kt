package com.collectiveidea.oauth

import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.NSObject

public class IosWebAuthSession(
    private val completionHandler: WebAuthSessionCompletionHandler,
) : WebAuthSession {
    override fun startSignIn(
        signInUrl: String,
        redirectUrl: String,
    ) {
        val authSession = ASWebAuthenticationSession(
            uRL = NSURL.URLWithString(signInUrl)!!,
            callbackURLScheme = NSURL.URLWithString(redirectUrl)!!.scheme,
            completionHandler = { callbackUrl, error ->
                completionHandler(
                    callbackUrl?.absoluteString,
                    error?.localizedDescription,
                )
            },
        )

        authSession.presentationContextProvider = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
            // https://developer.apple.com/documentation/authenticationservices/aswebauthenticationpresentationcontextproviding/presentationanchor(for:)?language=objc
            override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
                UIApplication.sharedApplication.keyWindow ?: ASPresentationAnchor()
        }

        authSession.prefersEphemeralWebBrowserSession = true
        authSession.start()
    }
}
