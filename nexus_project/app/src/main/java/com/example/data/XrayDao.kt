package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface XrayDao {
    // --- Subscriptions ---
    @Query("SELECT * FROM subscriptions ORDER BY lastUpdated DESC")
    fun getAllSubscriptions(): Flow<List<Subscription>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: Subscription): Long

    @Delete
    suspend fun deleteSubscription(subscription: Subscription)

    @Query("DELETE FROM xray_configs WHERE subscriptionId = :subId")
    suspend fun deleteConfigsBySubscription(subId: Int)

    // --- Configurations ---
    @Query("SELECT * FROM xray_configs ORDER BY isStarred DESC, name ASC")
    fun getAllConfigs(): Flow<List<XrayConfig>>

    @Query("SELECT * FROM xray_configs WHERE protocol = :protocol ORDER BY isStarred DESC, name ASC")
    fun getConfigsByProtocol(protocol: String): Flow<List<XrayConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: XrayConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<XrayConfig>)

    @Update
    suspend fun updateConfig(config: XrayConfig)

    @Delete
    suspend fun deleteConfig(config: XrayConfig)

    @Query("DELETE FROM xray_configs")
    suspend fun deleteAllConfigs()

    @Query("UPDATE xray_configs SET ping = :ping WHERE id = :configId")
    suspend fun updateConfigPing(configId: Int, ping: Int?)

    // --- Settings ---
    @Query("SELECT * FROM xray_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<XraySettings?>

    @Query("SELECT * FROM xray_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): XraySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: XraySettings)
}
