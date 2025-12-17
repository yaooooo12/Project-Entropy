package com.entropy.clicker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.entropy.clicker.data.model.ClickConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "entropy_configs")

/**
 * 配置仓库接口
 */
interface ConfigRepository {
    // 配置管理
    suspend fun saveConfig(config: ClickConfig)
    suspend fun getConfig(id: String): ClickConfig?
    suspend fun getAllConfigs(): List<ClickConfig>
    suspend fun deleteConfig(id: String)

    // 当前配置
    suspend fun setCurrentConfigId(id: String)
    suspend fun getCurrentConfig(): ClickConfig?
    val currentConfigFlow: Flow<ClickConfig?>
    val allConfigsFlow: Flow<List<ClickConfig>>
}

/**
 * 配置仓库实现
 */
@Singleton
class ConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ConfigRepository {

    companion object {
        private val CONFIGS_KEY = stringPreferencesKey("configs")
        private val CURRENT_CONFIG_ID_KEY = stringPreferencesKey("current_config_id")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveConfig(config: ClickConfig) {
        val updatedConfig = config.copy(updatedAt = System.currentTimeMillis())
        context.dataStore.edit { prefs ->
            val configs = getConfigsFromPrefs(prefs).toMutableList()
            val index = configs.indexOfFirst { it.id == updatedConfig.id }
            if (index >= 0) {
                configs[index] = updatedConfig
            } else {
                configs.add(updatedConfig)
            }
            prefs[CONFIGS_KEY] = json.encodeToString(configs)
        }
    }

    override suspend fun getConfig(id: String): ClickConfig? {
        return getAllConfigs().find { it.id == id }
    }

    override suspend fun getAllConfigs(): List<ClickConfig> {
        return context.dataStore.data.first().let { prefs ->
            getConfigsFromPrefs(prefs)
        }
    }

    override suspend fun deleteConfig(id: String) {
        context.dataStore.edit { prefs ->
            val configs = getConfigsFromPrefs(prefs).filter { it.id != id }
            prefs[CONFIGS_KEY] = json.encodeToString(configs)

            // 如果删除的是当前配置，清除当前配置ID
            if (prefs[CURRENT_CONFIG_ID_KEY] == id) {
                prefs.remove(CURRENT_CONFIG_ID_KEY)
            }
        }
    }

    override suspend fun setCurrentConfigId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_CONFIG_ID_KEY] = id
        }
    }

    override suspend fun getCurrentConfig(): ClickConfig? {
        return context.dataStore.data.first().let { prefs ->
            val currentId = prefs[CURRENT_CONFIG_ID_KEY]
            if (currentId != null) {
                getConfigsFromPrefs(prefs).find { it.id == currentId }
            } else {
                // 如果没有选中配置，返回第一个或默认配置
                getConfigsFromPrefs(prefs).firstOrNull() ?: ClickConfig.DEFAULT
            }
        }
    }

    override val currentConfigFlow: Flow<ClickConfig?>
        get() = context.dataStore.data.map { prefs ->
            val currentId = prefs[CURRENT_CONFIG_ID_KEY]
            val configs = getConfigsFromPrefs(prefs)
            if (currentId != null) {
                configs.find { it.id == currentId }
            } else {
                configs.firstOrNull() ?: ClickConfig.DEFAULT
            }
        }

    override val allConfigsFlow: Flow<List<ClickConfig>>
        get() = context.dataStore.data.map { prefs ->
            getConfigsFromPrefs(prefs)
        }

    private fun getConfigsFromPrefs(prefs: Preferences): List<ClickConfig> {
        val configsJson = prefs[CONFIGS_KEY]
        return if (configsJson.isNullOrEmpty()) {
            // 初始化默认配置
            listOf(ClickConfig.DEFAULT)
        } else {
            try {
                json.decodeFromString<List<ClickConfig>>(configsJson)
            } catch (e: Exception) {
                listOf(ClickConfig.DEFAULT)
            }
        }
    }
}
