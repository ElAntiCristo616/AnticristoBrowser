package com.elanticristo616.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CuteNovna Browser v1.1.0 — tests del parseo DLNA/UPnP.
 * Sin tele real: se prueban las funciones puras (parseo de
 * descripción, tiempos y metadatos DIDL-Lite).
 */
class DlnaParseTest {

    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <friendlyName>Tele del cuarto</friendlyName>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/upnp/control/AVTransport</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun `parseControlUrl resuelve la url relativa contra LOCATION`() {
        val ctrl = DlnaClient.parseControlUrl(
            "http://192.168.1.50:1400/xml/device_description.xml",
            description
        )
        assertEquals(
            "http://192.168.1.50:1400/upnp/control/AVTransport",
            ctrl
        )
    }

    @Test
    fun `parseControlUrl sin AVTransport devuelve null`() {
        assertNull(
            DlnaClient.parseControlUrl(
                "http://192.168.1.50:1400/xml/device_description.xml",
                "<root><device><friendlyName>X</friendlyName></device></root>"
            )
        )
    }

    @Test
    fun `parseFriendlyName saca el nombre`() {
        assertEquals("Tele del cuarto", DlnaClient.parseFriendlyName(description))
    }

    @Test
    fun `parseTimeToSeconds convierte hh mm ss`() {
        assertEquals(90.0, DlnaClient.parseTimeToSeconds("0:01:30"), 0.001)
        assertEquals(3661.0, DlnaClient.parseTimeToSeconds("1:01:01"), 0.001)
        assertEquals(45.5, DlnaClient.parseTimeToSeconds("0:00:45.5"), 0.001)
        assertEquals(-1.0, DlnaClient.parseTimeToSeconds("NOT_IMPLEMENTED"), 0.001)
        assertEquals(-1.0, DlnaClient.parseTimeToSeconds(""), 0.001)
    }

    @Test
    fun `secondsToRelTime formatea REL_TIME`() {
        assertEquals("0:01:30", DlnaClient.secondsToRelTime(90))
        assertEquals("1:01:01", DlnaClient.secondsToRelTime(3661))
        assertEquals("0:00:00", DlnaClient.secondsToRelTime(-5))
    }

    @Test
    fun `parsePositionInfo saca posicion y duracion`() {
        val xml = "<u:GetPositionInfoResponse>" +
            "<RelTime>0:02:15</RelTime>" +
            "<TrackDuration>0:10:00</TrackDuration>" +
            "</u:GetPositionInfoResponse>"
        val (pos, dur) = DlnaClient.parsePositionInfo(xml)!!
        assertEquals(135.0, pos, 0.001)
        assertEquals(600.0, dur, 0.001)
    }

    @Test
    fun `parsePositionInfo sin RelTime devuelve null`() {
        assertNull(DlnaClient.parsePositionInfo("<u:GetPositionInfoResponse/>"))
    }

    @Test
    fun `didlLite trae titulo y url con mime mp4`() {
        val meta = DlnaClient.didlLite("http://192.168.1.2:8080/v.mp4", "Mi video")
        assertEquals(true, meta.contains("Mi video"))
        assertEquals(true, meta.contains("video/mp4"))
        assertEquals(true, meta.contains("object.item.videoItem"))
    }

    @Test
    fun `didlLite escapa xml en el titulo`() {
        val meta = DlnaClient.didlLite("http://x/v.mp4", "A & B <C>")
        assertEquals(true, meta.contains("A &amp; B &lt;C&gt;"))
    }
}
