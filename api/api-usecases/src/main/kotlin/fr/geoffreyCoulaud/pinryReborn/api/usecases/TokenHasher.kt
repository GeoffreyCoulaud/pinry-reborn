package fr.geoffreyCoulaud.pinryReborn.api.usecases

import java.security.MessageDigest
import java.util.Locale

/**
 * SHA-256, as lowercase hexadecimal. A session token already holds 256 bits of entropy, so it needs no slow derivation;
 * an authentication attempt key is only grouped by its digest, never trusted as a secret, and wants the fixed width.
 */
object TokenHasher {
    fun sha256(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString(separator = "") {
            byte ->
            "%02x".format(Locale.ROOT, byte)
        }
}
