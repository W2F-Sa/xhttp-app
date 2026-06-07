package com.example.ui

import android.app.Application
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.vpn.NexusVpnService
import com.example.vpn.VpnServiceManager
import com.example.vpn.XrayConfigGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class XrayViewModel(application: Application) : AndroidViewModel(application) {
    private val db = XrayDatabase.getDatabase(application)
    private val repository = Repository(db.xrayDao())

    // --- Tab Selection ---
    private val _currentTab = MutableStateFlow("home")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    // --- Connection Core (using real VPN service) ---
    val isConnected: StateFlow<Boolean> = VpnServiceManager.isConnected
    val isConnecting: StateFlow<Boolean> = VpnServiceManager.isConnecting
    val connectionStatus: StateFlow<String> = VpnServiceManager.connectionStatus

    private val _selectedConfigId = MutableStateFlow<Int?>(null)
    val selectedConfigId: StateFlow<Int?> = _selectedConfigId.asStateFlow()

    // --- Real traffic stats from Xray core ---
    private val _downloadSpeed = MutableStateFlow("--")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _uploadSpeed = MutableStateFlow("--")
    val uploadSpeed: StateFlow<String> = _uploadSpeed.asStateFlow()

    // --- Subscription Sync State ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // --- VPN Permission ---
    private val _vpnPermissionNeeded = MutableStateFlow<Intent?>(null)
    val vpnPermissionNeeded: StateFlow<Intent?> = _vpnPermissionNeeded.asStateFlow()

    // --- Selected / Active Config ---
    val allConfigs: StateFlow<List<XrayConfig>> = repository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSubscriptions: StateFlow<List<Subscription>> = repository.allSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<XraySettings> = repository.settingsFlow
        .map { it ?: XraySettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XraySettings())

    private val httpClient = OkHttpClient()

    init {
        viewModelScope.launch {
            repository.settingsFlow.first().let { currentSettings ->
                if (currentSettings == null) {
                    repository.saveSettings(XraySettings())
                }
            }

            repository.allConfigs.first().let { currentConfigs ->
                if (currentConfigs.isNotEmpty()) {
                    _selectedConfigId.value = currentConfigs.firstOrNull { it.isStarred }?.id
                        ?: currentConfigs.firstOrNull()?.id
                }
            }
        }

        // Real speed stats collection loop
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (VpnServiceManager.isConnected.value) {
                    val downBytes = VpnServiceManager.downloadBytesPerSec.value
                    val upBytes = VpnServiceManager.uploadBytesPerSec.value
                    _downloadSpeed.value = formatSpeed(downBytes)
                    _uploadSpeed.value = formatSpeed(upBytes)
                } else {
                    _downloadSpeed.value = "--"
                    _uploadSpeed.value = "--"
                }
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val mbps = bytesPerSec * 8.0 / 1_000_000.0
        return if (mbps < 0.01) "0.0" else String.format("%.1f", mbps)
    }

    // Toggle Connection using REAL VPN Service
    fun toggleConnection() {
        val context = getApplication<Application>()

        if (VpnServiceManager.isConnected.value) {
            // Disconnect
            VpnServiceManager.stopVpn(context)
        } else {
            // Connect
            val config = allConfigs.value.firstOrNull { it.id == _selectedConfigId.value }
            if (config == null) {
                _syncMessage.value = "Select a configuration first!"
                return
            }

            // Check VPN permission
            val permissionIntent = VpnServiceManager.getVpnPermissionIntent(context)
            if (permissionIntent != null) {
                // Need user permission - emit signal to UI
                _vpnPermissionNeeded.value = permissionIntent
                return
            }

            // Permission granted, start VPN
            val currentSettings = settings.value
            VpnServiceManager.startVpn(context, config, currentSettings)
        }
    }

    // Called after user grants VPN permission from Activity
    fun onVpnPermissionGranted() {
        _vpnPermissionNeeded.value = null
        val context = getApplication<Application>()
        val config = allConfigs.value.firstOrNull { it.id == _selectedConfigId.value } ?: return
        val currentSettings = settings.value
        VpnServiceManager.startVpn(context, config, currentSettings)
    }

    fun onVpnPermissionDenied() {
        _vpnPermissionNeeded.value = null
        _syncMessage.value = "VPN permission denied. Cannot connect."
    }

    fun clearVpnPermissionRequest() {
        _vpnPermissionNeeded.value = null
    }

    fun selectConfig(id: Int) {
        _selectedConfigId.value = id
        // If connected, reconnect with new config
        if (VpnServiceManager.isConnected.value) {
            val context = getApplication<Application>()
            VpnServiceManager.stopVpn(context)
            viewModelScope.launch {
                delay(1000)
                toggleConnection()
            }
        }
    }

    fun toggleStarred(config: XrayConfig) {
        viewModelScope.launch {
            repository.updateConfig(config.copy(isStarred = !config.isStarred))
        }
    }

    fun deleteConfig(config: XrayConfig) {
        viewModelScope.launch {
            repository.deleteConfig(config)
            if (_selectedConfigId.value == config.id) {
                _selectedConfigId.value = allConfigs.value.filter { it.id != config.id }.firstOrNull()?.id
            }
        }
    }

    // Add Custom Configuration String
    fun addConfigFromString(urlString: String): Boolean {
        try {
            val trimmed = urlString.trim()
            val parsedConfig = parseXrayConfigUri(trimmed)
            if (parsedConfig != null) {
                viewModelScope.launch {
                    repository.insertConfig(parsedConfig)
                }
                return true
            }
        } catch (e: Exception) {
            Log.e("NEXUS", "Error adding config", e)
        }
        return false
    }

    // Parse vless:// vmess:// trojan:// ss:// uris
    private fun parseXrayConfigUri(uri: String): XrayConfig? {
        try {
            if (uri.startsWith("vless://")) {
                val rem = uri.substring(8)
                val atIdx = rem.indexOf('@')
                val colonIdx = rem.indexOf(':', atIdx)
                val qIdx = rem.indexOf('?', colonIdx)
                val hashIdx = rem.indexOf('#', colonIdx)

                val uuid = rem.substring(0, atIdx)
                val host = rem.substring(atIdx + 1, colonIdx)

                val endPortIdx = if (qIdx != -1) qIdx else if (hashIdx != -1) hashIdx else rem.length
                val port = rem.substring(colonIdx + 1, endPortIdx).toInt()

                var name = "VLESS Config"
                if (hashIdx != -1) {
                    name = java.net.URLDecoder.decode(rem.substring(hashIdx + 1), "UTF-8")
                }

                // Parse query params for transport type
                var transport = "tcp"
                if (qIdx != -1) {
                    val queryEnd = if (hashIdx != -1) hashIdx else rem.length
                    val queryStr = rem.substring(qIdx + 1, queryEnd)
                    val params = queryStr.split("&").associate {
                        val parts = it.split("=", limit = 2)
                        parts[0] to (if (parts.size > 1) parts[1] else "")
                    }
                    transport = params["type"] ?: "tcp"
                }

                return XrayConfig(
                    name = name,
                    protocol = "VLESS",
                    address = host,
                    port = port,
                    uuid = uuid,
                    streamTransport = transport,
                    originalConfigString = uri
                )
            } else if (uri.startsWith("trojan://")) {
                val rem = uri.substring(9)
                val atIdx = rem.indexOf('@')
                val colonIdx = rem.indexOf(':', atIdx)
                val qIdx = rem.indexOf('?', colonIdx)
                val hashIdx = rem.indexOf('#', colonIdx)

                val uuid = rem.substring(0, atIdx)
                val host = rem.substring(atIdx + 1, colonIdx)
                val endPortIdx = if (qIdx != -1) qIdx else if (hashIdx != -1) hashIdx else rem.length
                val port = rem.substring(colonIdx + 1, endPortIdx).toInt()

                var name = "Trojan Config"
                if (hashIdx != -1) {
                    name = java.net.URLDecoder.decode(rem.substring(hashIdx + 1), "UTF-8")
                }

                var transport = "tcp"
                if (qIdx != -1) {
                    val queryEnd = if (hashIdx != -1) hashIdx else rem.length
                    val queryStr = rem.substring(qIdx + 1, queryEnd)
                    val params = queryStr.split("&").associate {
                        val parts = it.split("=", limit = 2)
                        parts[0] to (if (parts.size > 1) parts[1] else "")
                    }
                    transport = params["type"] ?: "tcp"
                }

                return XrayConfig(
                    name = name,
                    protocol = "TROJAN",
                    address = host,
                    port = port,
                    uuid = uuid,
                    streamTransport = transport,
                    originalConfigString = uri
                )
            } else if (uri.startsWith("vmess://")) {
                val base64Str = uri.substring(8).trim()
                val decoded = String(Base64.decode(base64Str, Base64.DEFAULT))
                val idMatch = "\"id\":\"([^\"]+)\"".toRegex().find(decoded)
                val addMatch = "\"add\":\"([^\"]+)\"".toRegex().find(decoded)
                val portMatch = "\"port\":(\\d+)".toRegex().find(decoded) ?: "\"port\":\"(\\d+)\"".toRegex().find(decoded)
                val psMatch = "\"ps\":\"([^\"]+)\"".toRegex().find(decoded)
                val netMatch = "\"net\":\"([^\"]+)\"".toRegex().find(decoded)

                return XrayConfig(
                    name = psMatch?.groupValues?.get(1) ?: "VMess Config",
                    protocol = "VMESS",
                    address = addMatch?.groupValues?.get(1) ?: "127.0.0.1",
                    port = portMatch?.groupValues?.get(1)?.toIntOrNull() ?: 443,
                    uuid = idMatch?.groupValues?.get(1) ?: UUID.randomUUID().toString(),
                    streamTransport = netMatch?.groupValues?.get(1) ?: "ws",
                    originalConfigString = uri
                )
            }
        } catch (e: Exception) {
            Log.e("NEXUS", "Parsing config error", e)
        }
        return null
    }

    // Update Settings
    fun updateSettings(updated: XraySettings) {
        viewModelScope.launch {
            repository.saveSettings(updated)
        }
    }

    // Add Subscription Source
    fun addSubscription(name: String, url: String) {
        viewModelScope.launch {
            repository.insertSubscription(
                Subscription(
                    name = name,
                    url = url,
                    status = "Active"
                )
            )
            _syncMessage.value = "Subscription added successfully."
            syncSubscriptions()
        }
    }

    // Sync Subscription Sources - fetch real configs from URLs
    fun syncSubscriptions() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing subscriptions..."
            var totalAdded = 0

            try {
                val subs = repository.allSubscriptions.first()
                for (sub in subs) {
                    if (sub.status == "Expired") continue

                    val listString = fetchConfigsFromUrl(sub.url)
                    val discovered = mutableListOf<XrayConfig>()

                    if (listString.isNotEmpty()) {
                        var rawFeed = listString
                        // Try base64 decode if it doesn't contain protocol URIs
                        if (!listString.contains("://") && listString.length > 10) {
                            try {
                                rawFeed = String(Base64.decode(listString.trim(), Base64.DEFAULT))
                            } catch (_: Exception) {}
                        }

                        val lines = rawFeed.split("\n", "\r")
                        for (line in lines) {
                            val config = parseXrayConfigUri(line.trim())
                            if (config != null) {
                                discovered.add(config.copy(subscriptionId = sub.id))
                            }
                        }
                    }

                    if (discovered.isNotEmpty()) {
                        db.xrayDao().deleteConfigsBySubscription(sub.id)
                        repository.insertConfigs(discovered)
                        totalAdded += discovered.size
                    }

                    // Update sub timestamp
                    repository.insertSubscription(sub.copy(lastUpdated = System.currentTimeMillis()))
                }

                if (totalAdded > 0) {
                    _syncMessage.value = "Sync complete. Found $totalAdded nodes."
                } else {
                    _syncMessage.value = "Sync complete. No new configs found - check your subscription URLs."
                }

                val currentList = repository.allConfigs.first()
                if (_selectedConfigId.value == null || !currentList.any { it.id == _selectedConfigId.value }) {
                    _selectedConfigId.value = currentList.firstOrNull { it.isStarred }?.id ?: currentList.firstOrNull()?.id
                }
            } catch (e: Exception) {
                _syncMessage.value = "Sync failed: ${e.message}"
                Log.e("NEXUS", "Sync failure", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun fetchConfigsFromUrl(urlString: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(urlString).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() ?: "" else ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }

    fun removeSubscription(subscription: Subscription) {
        viewModelScope.launch {
            repository.deleteSubscription(subscription)
            _syncMessage.value = "Subscription removed."
            val remaining = repository.allConfigs.first()
            if (!remaining.any { it.id == _selectedConfigId.value }) {
                _selectedConfigId.value = remaining.firstOrNull { it.isStarred }?.id ?: remaining.firstOrNull()?.id
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    // --- Real TCPing Socket Engine ---
    suspend fun runTcping(host: String, port: Int): Int {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 3000)
                socket.close()
                (System.currentTimeMillis() - start).toInt()
            } catch (e: Exception) {
                -1
            }
        }
    }

    // Ping single node and save real result
    fun testNodePing(config: XrayConfig) {
        viewModelScope.launch {
            val realPing = runTcping(config.address, config.port)
            if (realPing > 0) {
                repository.updateConfigPing(config.id, realPing)
            } else {
                repository.updateConfigPing(config.id, null)
            }
        }
    }

    // Test all nodes - select fastest
    fun testAllPingsAndOptimize() {
        viewModelScope.launch {
            _syncMessage.value = "Testing all nodes..."
            val currentList = allConfigs.value
            for (config in currentList) {
                val realPing = runTcping(config.address, config.port)
                if (realPing > 0) {
                    repository.updateConfigPing(config.id, realPing)
                } else {
                    repository.updateConfigPing(config.id, null)
                }
            }
            val refreshedList = repository.allConfigs.first()
            val bestNode = refreshedList.filter { it.ping != null && it.ping > 0 }.minByOrNull { it.ping!! }
            if (bestNode != null) {
                _selectedConfigId.value = bestNode.id
                _syncMessage.value = "Best node: ${bestNode.name} (${bestNode.ping}ms)"
            } else {
                _syncMessage.value = "No reachable nodes found. Check your configs."
            }
        }
    }
}
