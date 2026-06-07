package com.example.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.example.data.XrayConfig
import com.example.data.XraySettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the VPN service lifecycle from the ViewModel.
 * Handles VPN permission, starting/stopping the service, and querying live stats.
 */
object VpnServiceManager {

    private const val TAG = "VpnServiceManager"

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Not Protected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _downloadBytesPerSec = MutableStateFlow(0L)
    val downloadBytesPerSec: StateFlow<Long> = _downloadBytesPerSec.asStateFlow()

    private val _uploadBytesPerSec = MutableStateFlow(0L)
    val uploadBytesPerSec: StateFlow<Long> = _uploadBytesPerSec.asStateFlow()

    private var statsJob: Job? = null
    private var lastDownloadBytes = 0L
    private var lastUploadBytes = 0L
    private var lastStatsTime = 0L

    /**
     * Check if VPN permission is already granted (no user prompt needed).
     */
    fun isVpnPermissionGranted(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    /**
     * Returns the VPN prepare intent if permission is needed, null if already granted.
     */
    fun getVpnPermissionIntent(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    /**
     * Start the VPN connection with the given config.
     */
    fun startVpn(context: Context, config: XrayConfig, settings: XraySettings) {
        _isConnecting.value = true
        _connectionStatus.value = "Connecting..."

        try {
            // Generate Xray JSON config
            val configJson = XrayConfigGenerator.generateConfig(config, settings)
            Log.d(TAG, "Generated Xray config for ${config.protocol} -> ${config.address}:${config.port}")

            // Start the VPN service
            val intent = Intent(context, NexusVpnService::class.java).apply {
                action = NexusVpnService.ACTION_START
                putExtra(NexusVpnService.EXTRA_CONFIG_JSON, configJson)
            }

            context.startService(intent)

            // Monitor connection establishment
            CoroutineScope(Dispatchers.Main).launch {
                // Wait for the service to report running
                var attempts = 0
                while (attempts < 30 && !NexusVpnService.isRunning) {
                    delay(200)
                    attempts++
                }

                if (NexusVpnService.isRunning) {
                    _isConnected.value = true
                    _isConnecting.value = false
                    _connectionStatus.value = "Protected"
                    startStatsPolling()
                    Log.i(TAG, "VPN connected successfully")
                } else {
                    _isConnected.value = false
                    _isConnecting.value = false
                    _connectionStatus.value = "Connection Failed"
                    Log.e(TAG, "VPN failed to start after timeout")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            _isConnected.value = false
            _isConnecting.value = false
            _connectionStatus.value = "Error: ${e.message}"
        }
    }

    /**
     * Stop the VPN connection.
     */
    fun stopVpn(context: Context) {
        _isConnecting.value = true
        _connectionStatus.value = "Disconnecting..."

        stopStatsPolling()

        val intent = Intent(context, NexusVpnService::class.java).apply {
            action = NexusVpnService.ACTION_STOP
        }
        context.startService(intent)

        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            _isConnected.value = false
            _isConnecting.value = false
            _connectionStatus.value = "Not Protected"
            _downloadBytesPerSec.value = 0L
            _uploadBytesPerSec.value = 0L
        }
    }

    /**
     * Polls traffic stats from the Xray core every second.
     */
    private fun startStatsPolling() {
        lastDownloadBytes = 0L
        lastUploadBytes = 0L
        lastStatsTime = System.currentTimeMillis()

        statsJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(1000)
                try {
                    val controller = NexusVpnService.coreController
                    if (controller != null && NexusVpnService.isRunning) {
                        // queryStats returns bytes since last query (delta)
                        val downDelta = controller.queryStats("proxy", "downlink")
                        val upDelta = controller.queryStats("proxy", "uplink")

                        withContext(Dispatchers.Main) {
                            _downloadBytesPerSec.value = downDelta
                            _uploadBytesPerSec.value = upDelta
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Stats query error", e)
                }
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }
}
