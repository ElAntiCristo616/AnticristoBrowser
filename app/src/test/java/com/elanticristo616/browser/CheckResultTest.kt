package com.elanticristo616.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motivo real en español del chequeo de conexión (v1.0.2). */
class CheckResultTest {

    @Test
    fun timeoutDiceSinRespuesta() {
        val r = RokuClient.CheckResult(false, null, timedOut = true, refused = false, null)
        assertEquals("sin respuesta (tiempo agotado)", r.detalle())
    }

    @Test
    fun conexionRechazada() {
        val r = RokuClient.CheckResult(false, null, timedOut = false, refused = true, null)
        assertEquals("conexión rechazada", r.detalle())
    }

    @Test
    fun errorHttpConCodigo() {
        val r = RokuClient.CheckResult(true, 404, timedOut = false, refused = false, null)
        assertTrue(r.reachable)
        assertEquals("error HTTP 404", r.detalle())
    }

    @Test
    fun cualquierHttpEsAlcanzable() {
        // Un 500 también prueba que algo escucha en el puerto 8060.
        val r = RokuClient.CheckResult(true, 500, timedOut = false, refused = false, null)
        assertTrue(r.reachable)
    }
}
