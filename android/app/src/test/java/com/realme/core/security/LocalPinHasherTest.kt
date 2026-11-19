package com.realme.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPinHasherTest {
    @Test
    fun correctPinVerifiesAndWrongPinFails() {
        val credentials = LocalPinHasher.create("1234")
        assertTrue(LocalPinHasher.verify("1234", credentials.salt, credentials.hash))
        assertFalse(LocalPinHasher.verify("9999", credentials.salt, credentials.hash))
    }

    @Test
    fun saltMakesIdenticalPinsProduceDifferentHashes() {
        val first = LocalPinHasher.create("1234")
        val second = LocalPinHasher.create("1234")
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pinMustContainFourToEightDigits() {
        LocalPinHasher.create("12ab")
    }
}
