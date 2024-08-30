package com.collectiveidea.oauth

import com.chrynan.krypt.csprng.SecureRandom
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.random.Random

/**
 * A utility class with helpers for implementing the Proof Key for Code Exchange (PKCE,
 * pronounced "pixy").
 *
 * Implementation inspired by [krypt-pkce](https://github.com/chRyNaN/krypt/blob/develop/krypt-pkce/src/commonMain/kotlin/com.chrynan.krypt.pkce/Pkce.kt)
 * but directly leveraging Okio for simplicity.
 *
 * @see [RFC-7636](https://datatracker.ietf.org/doc/html/rfc7636)
 */
class PKCEUtil(
    private val random: Random = SecureRandom(),
) {
    /**
     * Extension function for Okio to create URL-Safe Base64 encoding without
     * trailing padding, required for the PKCE flow.
     */
    private fun ByteString.base64UrlNoPadding(): String = base64Url().dropLastWhile { it == '=' }

    /**
     * Generates a "code_verifier" specified in the PKCE protocol. This value is considered a secure key.
     *
     * @see [RFC-7636](https://datatracker.ietf.org/doc/html/rfc7636#section-4.1)
     */
    fun generateCodeVerifier(byteLength: Int = 32): String {
        val bytes = random.nextBytes(byteLength)

        return bytes.toByteString().base64UrlNoPadding()
    }

    /**
     * Creates a "code_challenge", specified in the PKCE protocol, from the provided [verifier], using the provided
     * transformation [method]. If the provided [method] is [CodeChallengeMethod.PLAIN], then the [verifier] is simply
     * returned, otherwise, if the provided [method] is [CodeChallengeMethod.S256], an SHA-256 hash operation is
     * performed and the result is returned.
     *
     * Note that the code challenge is returned as URL-safe base64, minus trailing `=` padding, per Appendix A.
     *
     * @see [RFC-7636](https://datatracker.ietf.org/doc/html/rfc7636#section-4.2)
     * @see [RFC-7636: Appendix B](https://datatracker.ietf.org/doc/html/rfc7636#appendix-B)
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun createCodeChallenge(
        verifier: String,
        method: CodeChallengeMethod = CodeChallengeMethod.S256,
    ): String = when (method) {
        CodeChallengeMethod.PLAIN -> verifier
        CodeChallengeMethod.S256 -> verifier.encodeUtf8().sha256().base64UrlNoPadding()
    }

    /**
     * Represents the supported transformation methods in the PKCE protocol. Currently, the only supported methods are
     * [PLAIN] and [S256]. The [PLAIN] method performs no operation, while the [S256] performs an SHA-256 hash.
     *
     * Implementation inspired by [krypt-pkce](https://github.com/chRyNaN/krypt/blob/develop/krypt-pkce/src/commonMain/kotlin/com.chrynan.krypt.pkce/CodeChallengeMethod.kt)
     * but modified for simplicity.
     */
    enum class CodeChallengeMethod(
        val typeName: String,
    ) {
        PLAIN(typeName = "plain"),
        S256(typeName = "S256"),
    }
}
