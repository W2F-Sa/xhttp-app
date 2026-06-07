package com.example.data

import kotlinx.coroutines.flow.Flow

class Repository(private val dao: XrayDao) {
    val allSubscriptions: Flow<List<Subscription>> = dao.getAllSubscriptions()
    val allConfigs: Flow<List<XrayConfig>> = dao.getAllConfigs()
    val settingsFlow: Flow<XraySettings?> = dao.getSettingsFlow()

    fun getConfigsByProtocol(protocol: String): Flow<List<XrayConfig>> {
        return if (protocol == "ALL") dao.getAllConfigs() else dao.getConfigsByProtocol(protocol)
    }

    suspend fun insertSubscription(subscription: Subscription): Long {
        return dao.insertSubscription(subscription)
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        dao.deleteConfigsBySubscription(subscription.id)
        dao.deleteSubscription(subscription)
    }

    suspend fun insertConfigs(configs: List<XrayConfig>) {
        dao.insertConfigs(configs)
    }

    suspend fun insertConfig(config: XrayConfig): Long {
        return dao.insertConfig(config)
    }

    suspend fun updateConfig(config: XrayConfig) {
        dao.updateConfig(config)
    }

    suspend fun deleteConfig(config: XrayConfig) {
        dao.deleteConfig(config)
    }

    suspend fun deleteAllConfigs() {
        dao.deleteAllConfigs()
    }

    suspend fun updateConfigPing(configId: Int, ping: Int?) {
        dao.updateConfigPing(configId, ping)
    }

    suspend fun getSettingsDirect(): XraySettings? {
        return dao.getSettingsDirect()
    }

    suspend fun saveSettings(settings: XraySettings) {
        dao.insertSettings(settings)
    }
}
