package com.entropy.clicker.ui.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.data.model.ClickStylePreset
import com.entropy.clicker.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val configId: String? = savedStateHandle["configId"]

    private val _config = MutableStateFlow(ClickConfig.DEFAULT)
    val config: StateFlow<ClickConfig> = _config.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            if (configId != null) {
                configRepository.getConfig(configId)?.let {
                    _config.value = it
                }
            }
        }
    }

    /**
     * 通过ID加载配置（用于BottomSheet模式）
     */
    fun loadConfigById(id: String) {
        // 重置保存状态，防止缓存的 ViewModel 导致弹窗立即关闭
        _isSaved.value = false
        viewModelScope.launch {
            configRepository.getConfig(id)?.let {
                _config.value = it
            }
        }
    }

    fun updateConfig(update: (ClickConfig) -> ClickConfig) {
        _config.value = update(_config.value)
    }

    fun saveConfig() {
        viewModelScope.launch {
            configRepository.saveConfig(_config.value)
            _isSaved.value = true
        }
    }

    // 便捷更新方法
    fun updateName(name: String) = updateConfig { it.copy(name = name) }

    fun updateEnableCenterTap(enabled: Boolean) = updateConfig { it.copy(enableCenterTap = enabled) }
    fun updateCenterFloatRangeX(value: Int) = updateConfig { it.copy(centerFloatRangeX = value) }
    fun updateCenterFloatRangeY(value: Int) = updateConfig { it.copy(centerFloatRangeY = value) }

    fun updateReactionTimeMin(value: Long) = updateConfig { it.copy(reactionTimeMin = value) }
    fun updateReactionTimeMax(value: Long) = updateConfig { it.copy(reactionTimeMax = value) }

    fun updateLikeAnchorXRatio(value: Float) = updateConfig { it.copy(likeAnchorXRatio = value) }
    fun updateLikeAnchorYRatio(value: Float) = updateConfig { it.copy(likeAnchorYRatio = value) }
    fun updateLikeJitterRadius(value: Int) = updateConfig { it.copy(likeJitterRadius = value) }

    fun updateDriftProbability(value: Float) = updateConfig { it.copy(driftProbability = value) }
    fun updateDriftRange(value: Int) = updateConfig { it.copy(driftRange = value) }

    fun updateBurstIntervalMin(value: Long) = updateConfig { it.copy(burstIntervalMin = value) }
    fun updateBurstIntervalMax(value: Long) = updateConfig { it.copy(burstIntervalMax = value) }
    fun updatePauseIntervalMin(value: Long) = updateConfig { it.copy(pauseIntervalMin = value) }
    fun updatePauseIntervalMax(value: Long) = updateConfig { it.copy(pauseIntervalMax = value) }
    fun updatePauseProbability(value: Float) = updateConfig { it.copy(pauseProbability = value) }

    fun updatePressDurationBase(value: Long) = updateConfig { it.copy(pressDurationBase = value) }
    fun updatePressDurationVariance(value: Int) = updateConfig { it.copy(pressDurationVariance = value) }

    fun updateMaxClickCount(value: Int) = updateConfig { it.copy(maxClickCount = value) }
    fun updateMaxRunDuration(value: Long) = updateConfig { it.copy(maxRunDuration = value) }

    // UI/UX 2.0 新增方法
    fun updateStylePreset(preset: ClickStylePreset) = updateConfig {
        it.copy(stylePreset = preset, useCustomTiming = false)
    }

    fun enableCustomTiming() = updateConfig { it.copy(useCustomTiming = true) }

    /**
     * 处理瞄准镜设置结果
     */
    fun updateFromScopeResult(xRatio: Float, yRatio: Float, jitterRadius: Int) = updateConfig {
        it.copy(
            likeAnchorXRatio = xRatio,
            likeAnchorYRatio = yRatio,
            likeJitterRadius = jitterRadius,
            positionSetByScope = true
        )
    }
}
