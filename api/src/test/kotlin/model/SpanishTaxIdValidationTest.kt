package model

import eu.kasify.model.api.requests.isValidSpanishTaxId
import eu.kasify.model.api.requests.normalizeSpanishTaxId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpanishTaxIdValidationTest {
    @Test
    fun `accepts a DNI with the correct control letter`() {
        assertTrue(isValidSpanishTaxId("12345678Z"))
        assertTrue(isValidSpanishTaxId("00000000T"))
    }

    @Test
    fun `accepts lowercase and separators`() {
        assertTrue(isValidSpanishTaxId("12345678z"))
        assertTrue(isValidSpanishTaxId("12345678-Z"))
        assertTrue(isValidSpanishTaxId("12 345 678 Z"))
    }

    @Test
    fun `rejects a DNI with a wrong control letter`() {
        assertFalse(isValidSpanishTaxId("12345678A"))
    }

    @Test
    fun `accepts a NIE with the correct control letter`() {
        // X1234567L: X→0, 01234567 % 23 = 19 → 'L'
        assertTrue(isValidSpanishTaxId("X1234567L"))
        assertFalse(isValidSpanishTaxId("X1234567Z"))
    }

    @Test
    fun `accepts K, L and M NIFs with the correct control letter`() {
        // 1234567 % 23 = 19 → 'L'
        assertTrue(isValidSpanishTaxId("K1234567L"))
        assertTrue(isValidSpanishTaxId("L1234567L"))
        assertTrue(isValidSpanishTaxId("M1234567L"))
    }

    @Test
    fun `rejects K, L and M NIFs with a wrong control letter`() {
        assertFalse(isValidSpanishTaxId("K1234567B"))
        // 'J' would pass the old CIF format branch; K ids must use the mod-23 letter instead.
        assertFalse(isValidSpanishTaxId("K1234567J"))
        assertFalse(isValidSpanishTaxId("M1234567A"))
    }

    @Test
    fun `accepts a CIF by format`() {
        assertTrue(isValidSpanishTaxId("B12345678"))
        assertTrue(isValidSpanishTaxId("A1234567J"))
    }

    @Test
    fun `normalizes to uppercase without separators`() {
        assertEquals("12345678Z", normalizeSpanishTaxId("12 345 678-z"))
        assertEquals("K1234567L", normalizeSpanishTaxId(" k-1234567-l "))
        assertEquals("", normalizeSpanishTaxId("  "))
    }

    @Test
    fun `rejects garbage`() {
        assertFalse(isValidSpanishTaxId(""))
        assertFalse(isValidSpanishTaxId("not-a-nif"))
        assertFalse(isValidSpanishTaxId("1234Z"))
        assertFalse(isValidSpanishTaxId("I1234567J")) // 'I' is not a valid CIF prefix
    }
}
