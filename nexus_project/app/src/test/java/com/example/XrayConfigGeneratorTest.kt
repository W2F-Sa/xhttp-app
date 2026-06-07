package com.example

import com.example.data.XrayConfig
import com.example.data.XraySettings
import com.example.vpn.XrayConfigGenerator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates that the generated Xray JSON configuration is well-formed and structurally
 * correct for each supported protocol and transport. This protects against malformed
 * config that would cause the Xray core to fail to start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XrayConfigGeneratorTest {

    private fun baseConfig() = XrayConfig(
        id = 1,
        name = "Test",
        protocol = "VLESS",
        address = "example.com",
        port = 443,
        uuid = "11111111-2222-3333-4444-555555555555",
        streamTransport = "tcp"
    )

    private fun parse(config: XrayConfig, settings: XraySettings = XraySettings()): JSONObject {
        val json = XrayConfigGenerator.generateConfig(config, settings)
        // Must be valid JSON
        return JSONObject(json)
    }

    @Test
    fun `vless tcp config is valid and has proxy outbound`() {
        val root = parse(baseConfig())
        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }
        assertEquals("vless", proxy.getString("protocol"))
        val vnext = proxy.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("example.com", vnext.getString("address"))
        assertEquals(443, vnext.getInt("port"))
    }

    @Test
    fun `socks inbound is on expected port`() {
        val root = parse(baseConfig())
        val inbounds = root.getJSONArray("inbounds")
        val socks = inbounds.getJSONObject(0)
        assertEquals("socks", socks.getString("protocol"))
        assertEquals(10808, socks.getInt("port"))
        assertEquals("127.0.0.1", socks.getString("listen"))
    }

    @Test
    fun `vmess config produces vmess outbound`() {
        val root = parse(baseConfig().copy(protocol = "VMESS", streamTransport = "ws"))
        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }
        assertEquals("vmess", proxy.getString("protocol"))
        assertEquals("ws", proxy.getJSONObject("streamSettings").getString("network"))
    }

    @Test
    fun `trojan config produces trojan outbound with password`() {
        val root = parse(baseConfig().copy(protocol = "TROJAN"))
        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }
        assertEquals("trojan", proxy.getString("protocol"))
        val server = proxy.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("11111111-2222-3333-4444-555555555555", server.getString("password"))
    }

    @Test
    fun `xhttp transport is mapped correctly`() {
        val root = parse(baseConfig().copy(streamTransport = "xhttp"))
        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }
        val stream = proxy.getJSONObject("streamSettings")
        assertEquals("xhttp", stream.getString("network"))
        assertTrue(stream.has("xhttpSettings"))
    }

    @Test
    fun `routing has direct block proxy and dns outbounds`() {
        val root = parse(baseConfig())
        val outbounds = root.getJSONArray("outbounds")
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
        assertTrue(tags.contains("proxy"))
        assertTrue(tags.contains("direct"))
        assertTrue(tags.contains("block"))
        assertTrue(tags.contains("dns-out"))
    }

    @Test
    fun `stats block present for traffic monitoring`() {
        val root = parse(baseConfig())
        assertNotNull(root.getJSONObject("stats"))
        val system = root.getJSONObject("policy").getJSONObject("system")
        assertTrue(system.getBoolean("statsOutboundUplink"))
        assertTrue(system.getBoolean("statsOutboundDownlink"))
    }

    @Test
    fun `fragment setting adds sockopt fragment`() {
        val root = parse(baseConfig(), XraySettings(fragmentEnabled = true))
        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }
        val sockopt = proxy.getJSONObject("streamSettings").getJSONObject("sockopt")
        assertTrue(sockopt.has("fragment"))
    }

    @Test
    fun `mux setting enables mux on proxy`() {
        val root = parse(baseConfig(), XraySettings(muxEnabled = true))
        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }
        assertTrue(proxy.getJSONObject("mux").getBoolean("enabled"))
    }
}
