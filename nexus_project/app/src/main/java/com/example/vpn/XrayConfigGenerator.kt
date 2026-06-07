package com.example.vpn

import com.example.data.XrayConfig
import com.example.data.XraySettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates a valid Xray JSON configuration from parsed XrayConfig entity and user settings.
 * Supports VLESS, VMess, Trojan, and Reality protocols.
 * Supports xhttp, ws, grpc, tcp stream transports.
 */
object XrayConfigGenerator {

    fun generateConfig(
        config: XrayConfig,
        settings: XraySettings
    ): String {
        val root = JSONObject()

        // Stats for traffic monitoring
        root.put("stats", JSONObject())

        // Policy for stats
        val policy = JSONObject()
        val systemPolicy = JSONObject()
        systemPolicy.put("statsOutboundUplink", true)
        systemPolicy.put("statsOutboundDownlink", true)
        policy.put("system", systemPolicy)
        root.put("policy", policy)

        // Log
        val log = JSONObject()
        log.put("loglevel", "warning")
        root.put("log", log)

        // DNS
        root.put("dns", buildDns(settings))

        // Inbounds (socks + tun)
        root.put("inbounds", buildInbounds(settings))

        // Outbounds
        root.put("outbounds", buildOutbounds(config, settings))

        // Routing
        root.put("routing", buildRouting(settings))

        return root.toString(2)
    }

    private fun buildDns(settings: XraySettings): JSONObject {
        val dns = JSONObject()
        val servers = JSONArray()

        servers.put(settings.primaryDns)
        servers.put(settings.secondaryDns)

        dns.put("servers", servers)

        if (settings.dnsStrategy != "prefer_ipv4") {
            dns.put("queryStrategy", when(settings.dnsStrategy) {
                "ipv4_only" -> "UseIPv4"
                "ipv6_only" -> "UseIPv6"
                "prefer_ipv6" -> "UseIP"
                else -> "UseIP"
            })
        }

        return dns
    }

    private fun buildInbounds(settings: XraySettings): JSONArray {
        val inbounds = JSONArray()

        // SOCKS inbound for local proxy
        val socksIn = JSONObject()
        socksIn.put("tag", "socks-in")
        socksIn.put("protocol", "socks")
        socksIn.put("listen", "127.0.0.1")
        socksIn.put("port", 10808)
        val socksSettings = JSONObject()
        socksSettings.put("udp", true)
        socksIn.put("settings", socksSettings)
        val socksSniffing = JSONObject()
        socksSniffing.put("enabled", true)
        socksSniffing.put("destOverride", JSONArray().put("http").put("tls"))
        socksSniffing.put("routeOnly", true)
        socksIn.put("sniffing", socksSniffing)
        inbounds.put(socksIn)

        return inbounds
    }

    private fun buildOutbounds(config: XrayConfig, settings: XraySettings): JSONArray {
        val outbounds = JSONArray()

        // Main proxy outbound
        val proxyOut = JSONObject()
        proxyOut.put("tag", "proxy")

        when (config.protocol.uppercase()) {
            "VLESS" -> {
                proxyOut.put("protocol", "vless")
                val vlessSettings = JSONObject()
                val vnext = JSONArray()
                val server = JSONObject()
                server.put("address", config.address)
                server.put("port", config.port)
                val users = JSONArray()
                val user = JSONObject()
                user.put("id", config.uuid)
                user.put("encryption", "none")
                user.put("flow", "")
                users.put(user)
                server.put("users", users)
                vnext.put(server)
                vlessSettings.put("vnext", vnext)
                proxyOut.put("settings", vlessSettings)
            }
            "VMESS" -> {
                proxyOut.put("protocol", "vmess")
                val vmessSettings = JSONObject()
                val vnext = JSONArray()
                val server = JSONObject()
                server.put("address", config.address)
                server.put("port", config.port)
                val users = JSONArray()
                val user = JSONObject()
                user.put("id", config.uuid)
                user.put("alterId", 0)
                user.put("security", "auto")
                users.put(user)
                server.put("users", users)
                vnext.put(server)
                vmessSettings.put("vnext", vnext)
                proxyOut.put("settings", vmessSettings)
            }
            "TROJAN" -> {
                proxyOut.put("protocol", "trojan")
                val trojanSettings = JSONObject()
                val servers = JSONArray()
                val server = JSONObject()
                server.put("address", config.address)
                server.put("port", config.port)
                server.put("password", config.uuid)
                servers.put(server)
                trojanSettings.put("servers", servers)
                proxyOut.put("settings", trojanSettings)
            }
            "REALITY" -> {
                proxyOut.put("protocol", "vless")
                val realitySettings = JSONObject()
                val vnext = JSONArray()
                val server = JSONObject()
                server.put("address", config.address)
                server.put("port", config.port)
                val users = JSONArray()
                val user = JSONObject()
                user.put("id", config.uuid)
                user.put("encryption", "none")
                user.put("flow", "xtls-rprx-vision")
                users.put(user)
                server.put("users", users)
                vnext.put(server)
                realitySettings.put("vnext", vnext)
                proxyOut.put("settings", realitySettings)
            }
        }

        // Stream settings
        proxyOut.put("streamSettings", buildStreamSettings(config, settings))

        // Mux settings
        if (settings.muxEnabled) {
            val mux = JSONObject()
            mux.put("enabled", true)
            mux.put("concurrency", 8)
            proxyOut.put("mux", mux)
        }

        outbounds.put(proxyOut)

        // Direct outbound
        val directOut = JSONObject()
        directOut.put("tag", "direct")
        directOut.put("protocol", "freedom")
        directOut.put("settings", JSONObject())
        outbounds.put(directOut)

        // Block outbound
        val blockOut = JSONObject()
        blockOut.put("tag", "block")
        blockOut.put("protocol", "blackhole")
        blockOut.put("settings", JSONObject())
        outbounds.put(blockOut)

        // DNS outbound
        val dnsOut = JSONObject()
        dnsOut.put("tag", "dns-out")
        dnsOut.put("protocol", "dns")
        outbounds.put(dnsOut)

        return outbounds
    }

    private fun buildStreamSettings(config: XrayConfig, settings: XraySettings): JSONObject {
        val stream = JSONObject()

        when (config.streamTransport.lowercase()) {
            "ws" -> {
                stream.put("network", "ws")
                val wsSettings = JSONObject()
                wsSettings.put("path", "/")
                stream.put("wsSettings", wsSettings)
                // TLS
                stream.put("security", "tls")
                val tlsSettings = JSONObject()
                tlsSettings.put("allowInsecure", settings.allowInsecure)
                stream.put("tlsSettings", tlsSettings)
            }
            "grpc" -> {
                stream.put("network", "grpc")
                val grpcSettings = JSONObject()
                grpcSettings.put("serviceName", "")
                grpcSettings.put("multiMode", false)
                stream.put("grpcSettings", grpcSettings)
                stream.put("security", "tls")
                val tlsSettings = JSONObject()
                tlsSettings.put("allowInsecure", settings.allowInsecure)
                stream.put("tlsSettings", tlsSettings)
            }
            "tcp" -> {
                stream.put("network", "tcp")
                if (config.protocol.uppercase() == "REALITY") {
                    stream.put("security", "reality")
                    val realitySettings = JSONObject()
                    realitySettings.put("fingerprint", "chrome")
                    realitySettings.put("serverName", "")
                    realitySettings.put("publicKey", "")
                    realitySettings.put("shortId", "")
                    stream.put("realitySettings", realitySettings)
                } else {
                    stream.put("security", "tls")
                    val tlsSettings = JSONObject()
                    tlsSettings.put("allowInsecure", settings.allowInsecure)
                    stream.put("tlsSettings", tlsSettings)
                }
            }
            "xhttp", "splithttp" -> {
                stream.put("network", "xhttp")
                val xhttpSettings = JSONObject()
                xhttpSettings.put("path", "/")
                stream.put("xhttpSettings", xhttpSettings)
                stream.put("security", "tls")
                val tlsSettings = JSONObject()
                tlsSettings.put("allowInsecure", settings.allowInsecure)
                stream.put("tlsSettings", tlsSettings)
            }
            else -> {
                stream.put("network", "tcp")
                stream.put("security", "tls")
                val tlsSettings = JSONObject()
                tlsSettings.put("allowInsecure", settings.allowInsecure)
                stream.put("tlsSettings", tlsSettings)
            }
        }

        // Fragment settings
        if (settings.fragmentEnabled) {
            val sockopt = JSONObject()
            val fragment = JSONObject()
            fragment.put("packets", "tlshello")
            fragment.put("length", "100-200")
            fragment.put("interval", "10-20")
            sockopt.put("fragment", fragment)
            stream.put("sockopt", sockopt)
        }

        return stream
    }

    private fun buildRouting(settings: XraySettings): JSONObject {
        val routing = JSONObject()
        routing.put("domainStrategy", "AsIs")

        val rules = JSONArray()

        // DNS rule
        val dnsRule = JSONObject()
        dnsRule.put("type", "field")
        dnsRule.put("port", "53")
        dnsRule.put("outboundTag", "dns-out")
        rules.put(dnsRule)

        // Block private/LAN IPs from going through proxy (explicit CIDRs - no geo file needed)
        val privateRule = JSONObject()
        privateRule.put("type", "field")
        val privateIps = JSONArray()
            .put("10.0.0.0/8")
            .put("172.16.0.0/12")
            .put("192.168.0.0/16")
            .put("127.0.0.0/8")
            .put("169.254.0.0/16")
            .put("224.0.0.0/4")
            .put("::1/128")
            .put("fc00::/7")
            .put("fe80::/10")
        privateRule.put("ip", privateIps)
        privateRule.put("outboundTag", "direct")
        rules.put(privateRule)

        // Route everything else through proxy
        val defaultRule = JSONObject()
        defaultRule.put("type", "field")
        defaultRule.put("port", "0-65535")
        defaultRule.put("outboundTag", "proxy")
        rules.put(defaultRule)

        routing.put("rules", rules)
        return routing
    }
}
