package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.util.Log
import com.example.MainActivity
import com.v2ray.ang.service.TProxyService
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/**
 * Real Xray VPN service.
 *
 * Architecture (same as v2rayNG default):
 *  1. Establish an Android TUN interface via VpnService.Builder.
 *  2. Start the Xray core (libv2ray) which exposes a local SOCKS5 proxy on 127.0.0.1:10808.
 *  3. Start hev-socks5-tunnel (libhev-socks5-tunnel.so) which reads packets from the TUN fd
 *     and forwards them to the SOCKS5 proxy. This is what makes real traffic flow.
 *  4. Exclude our own app from the VPN so the Xray core's sockets reach the real server
 *     without looping back into the tunnel.
 */
class NexusVpnService : VpnService(), CoreCallbackHandler {

    companion object {
        const val TAG = "NexusVpnService"
        const val ACTION_START = "com.example.vpn.START"
        const val ACTION_STOP = "com.example.vpn.STOP"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "nexus_vpn_channel"

        // Must match XrayConfigGenerator socks inbound + hev config
        const val SOCKS_PORT = 10808
        const val VPN_MTU = 1500
        const val PRIVATE_VLAN4_CLIENT = "10.10.14.1"
        const val LOOPBACK = "127.0.0.1"

        var isRunning: Boolean = false
            private set

        var coreController: CoreController? = null
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunStarted = false

    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: ""
                if (configJson.isNotEmpty()) {
                    startVpn(configJson)
                } else {
                    Log.e(TAG, "Empty config, stopping")
                    stopSelf()
                }
            }
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(configJson: String) {
        try {
            // 1. Init Xray core environment (geo assets bundled in AAR)
            val assetsPath = applicationContext.filesDir.absolutePath
            File(assetsPath).mkdirs()
            Libv2ray.initCoreEnv(assetsPath, "")

            // 2. Build the TUN interface
            val builder = Builder()
            builder.setSession("Nexus VPN")
            builder.setMtu(VPN_MTU)
            builder.addAddress(PRIVATE_VLAN4_CLIENT, 30)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")

            // Exclude self so Xray's outbound sockets don't loop back into the tunnel
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not exclude self package", e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopVpn()
                return
            }

            // 3. Start Xray core (SOCKS proxy). fd=0 => core does NOT touch the TUN itself.
            coreController = Libv2ray.newCoreController(this)
            coreController?.startLoop(configJson, 0)

            if (coreController?.isRunning != true) {
                Log.e(TAG, "Xray core failed to start")
                stopVpn()
                return
            }

            // 4. Start hev-socks5-tunnel to bridge TUN <-> SOCKS proxy
            startHevTunnel(vpnInterface!!.fd)

            isRunning = true
            startForeground(NOTIFICATION_ID, createNotification("Connected"))
            Log.i(TAG, "VPN started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
        }
    }

    private fun startHevTunnel(fd: Int) {
        val config = buildHevConfig()
        val configFile = File(filesDir, "hev-socks5-tunnel.yaml").apply {
            writeText(config)
        }
        try {
            TProxyService.TProxyStartService(configFile.absolutePath, fd)
            tunStarted = true
            Log.i(TAG, "hev-socks5-tunnel started")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start hev-socks5-tunnel", e)
            throw e
        }
    }

    private fun buildHevConfig(): String {
        return buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $VPN_MTU")
            appendLine("  ipv4: $PRIVATE_VLAN4_CLIENT")
            appendLine("socks5:")
            appendLine("  port: $SOCKS_PORT")
            appendLine("  address: $LOOPBACK")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
    }

    private fun stopVpn() {
        isRunning = false

        // Stop tun2socks first
        if (tunStarted) {
            try {
                TProxyService.TProxyStopService()
            } catch (e: Throwable) {
                Log.e(TAG, "Error stopping hev tunnel", e)
            }
            tunStarted = false
        }

        // Stop Xray core
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping core", e)
        }
        coreController = null

        // Close TUN
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interface", e)
        }
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked")
        stopVpn()
    }

    override fun onDestroy() {
        if (isRunning) stopVpn()
        super.onDestroy()
    }

    // --- CoreCallbackHandler ---
    override fun onEmitStatus(code: Long, message: String?): Long {
        Log.d(TAG, "Core status: $code $message")
        return 0
    }

    override fun shutdown(): Long {
        Log.d(TAG, "Core shutdown callback")
        return 0
    }

    override fun startup(): Long {
        Log.d(TAG, "Core startup callback")
        return 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nexus VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps VPN connection alive"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Nexus VPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
