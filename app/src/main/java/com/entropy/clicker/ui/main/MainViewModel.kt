package com.entropy.clicker.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.data.repository.ConfigRepository
import com.entropy.clicker.service.ClickAccessibilityService
import com.entropy.clicker.service.FloatingBallService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val configs: List<ClickConfig> = emptyList(),
    val currentConfig: ClickConfig? = null,
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayEnabled: Boolean = false,
    val isFloatingBallRunning: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadConfigs()
        checkPermissions()
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            configRepository.allConfigsFlow.collect { configs ->
                val current = configRepository.getCurrentConfig()
                _uiState.value = _uiState.value.copy(
                    configs = configs,
                    currentConfig = current
                )
            }
        }
    }

    fun checkPermissions() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val overlayEnabled = Settings.canDrawOverlays(context)

        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = accessibilityEnabled,
            isOverlayEnabled = overlayEnabled
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // 方法1: 检查服务实例是否存在
        if (ClickAccessibilityService.instance != null) {
            return true
        }

        // 方法2: 通过 Settings.Secure 检查
        val serviceId = "${context.packageName}/${ClickAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        return enabledServices.contains(serviceId) || enabledServices.contains(context.packageName)
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun selectConfig(config: ClickConfig) {
        viewModelScope.launch {
            configRepository.setCurrentConfigId(config.id)
            _uiState.value = _uiState.value.copy(currentConfig = config)
            // 更新悬浮球服务的配置
            FloatingBallService.setConfig(config)
        }
    }

    fun deleteConfig(config: ClickConfig) {
        viewModelScope.launch {
            configRepository.deleteConfig(config.id)
        }
    }

    fun startFloatingBall() {
        val config = _uiState.value.currentConfig ?: return

        if (!_uiState.value.isAccessibilityEnabled) {
            openAccessibilitySettings()
            return
        }

        if (!_uiState.value.isOverlayEnabled) {
            openOverlaySettings()
            return
        }

        FloatingBallService.setConfig(config)
        val intent = Intent(context, FloatingBallService::class.java)
        context.startForegroundService(intent)

        _uiState.value = _uiState.value.copy(isFloatingBallRunning = true)
    }

    fun stopFloatingBall() {
        context.stopService(Intent(context, FloatingBallService::class.java))
        ClickAccessibilityService.instance?.stopClicking()
        _uiState.value = _uiState.value.copy(isFloatingBallRunning = false)
    }

    fun createNewConfig(): ClickConfig {
        val newConfig = ClickConfig(
            name = "配置 ${_uiState.value.configs.size + 1}"
        )
        viewModelScope.launch {
            configRepository.saveConfig(newConfig)
        }
        return newConfig
    }

    /**
     * 刷新配置列表（保存后调用）
     */
    fun refreshConfigs() {
        viewModelScope.launch {
            val current = configRepository.getCurrentConfig()
            _uiState.value = _uiState.value.copy(currentConfig = current)
        }
    }
}
