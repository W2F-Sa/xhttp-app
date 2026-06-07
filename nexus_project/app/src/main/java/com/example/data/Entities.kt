package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val status: String, // "Active", "Expired"
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "xray_configs")
data class XrayConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subscriptionId: Int? = null,
    val name: String,
    val protocol: String, // "VLESS", "VMess", "TROJAN", "REALITY", "SS"
    val address: String,
    val port: Int,
    val uuid: String,
    val streamTransport: String, // "TCP", "WS", "GRPC", "xhttp"
    val ping: Int? = null, // latency in milliseconds
    val isStarred: Boolean = false,
    val originalConfigString: String = ""
)

@Entity(tableName = "xray_settings")
data class XraySettings(
    @PrimaryKey val id: Int = 1,
    val localDns: Boolean = true,
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "1.0.0.1",
    val fakeDns: Boolean = true,
    val dnsStrategy: String = "prefer_ipv4",
    val muxEnabled: Boolean = false,
    val fragmentEnabled: Boolean = false,
    val startOnBoot: Boolean = true,
    val allowInsecure: Boolean = false,
    val xrayVersion: String = "26.4.5",
    val xhttpEnabled: Boolean = true
)
