package com.elanticristo616.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validación IPv4 de la puerta (MainActivity.isValidIpv4). */
class IpValidationTest {

    @Test
    fun ipValida() {
        assertTrue(isValidIpv4("192.168.1.5"))
    }

    @Test
    fun ipSinPuntosEsInvalida() {
        assertFalse(isValidIpv4("1921681.78"))
    }

    @Test
    fun octetoMayorDe255EsInvalido() {
        assertFalse(isValidIpv4("192.168.1.300"))
    }

    @Test
    fun ipIncompletaEsInvalida() {
        assertFalse(isValidIpv4("192.168.1"))
    }

    @Test
    fun casosExtra() {
        assertTrue(isValidIpv4("0.0.0.0"))
        assertTrue(isValidIpv4("255.255.255.255"))
        assertFalse(isValidIpv4("192.168.1.5.6"))
        assertFalse(isValidIpv4("192.168.1.x"))
        assertFalse(isValidIpv4(""))
    }
}
