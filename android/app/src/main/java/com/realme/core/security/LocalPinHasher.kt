package com.realme.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinCredentials(val salt: String, val hash: String)

object LocalPinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun create(pin: String): PinCredentials {
        validatePin(pin)
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return PinCredentials(
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder().encodeToString(derive(pin, salt)),
        )
    }

    fun verify(pin: String, saltBase64: String, expectedHash: String): Boolean {
        if (pin.length !in 4..8 || pin.any { !it.isDigit() }) return false
        val salt = runCatching { Base64.getDecoder().decode(saltBase64) }.getOrNull() ?: return false
        val actual = runCatching { derive(pin, salt) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(expectedHash) }.getOrNull() ?: return false
        return MessageDigest.isEqual(actual, expected)
    }

    fun validatePin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) {
            "PINは4〜8桁の数字で入力してください"
        }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
