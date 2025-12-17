package com.entropy.clicker.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.service.OverlaySetupService
import com.entropy.clicker.ui.config.ConfigEditBottomSheet
import com.entropy.clicker.ui.config.ConfigEditViewModel
import com.entropy.clicker.ui.theme.ProjectEntropyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var scopeResultCallback: ((Float, Float, Int) -> Unit)? = null

    private val scopeResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action == OverlaySetupService.ACTION_SCOPE_RESULT) {
                val cancelled = intent.getBooleanExtra(OverlaySetupService.EXTRA_CANCELLED, false)
                if (!cancelled) {
                    val xRatio = intent.getFloatExtra(OverlaySetupService.EXTRA_X_RATIO, 0.85f)
                    val yRatio = intent.getFloatExtra(OverlaySetupService.EXTRA_Y_RATIO, 0.80f)
                    val jitterRadius = intent.getIntExtra(OverlaySetupService.EXTRA_JITTER_RADIUS, 50)
                    scopeResultCallback?.invoke(xRatio, yRatio, jitterRadius)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册广播接收器
        val filter = IntentFilter(OverlaySetupService.ACTION_SCOPE_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scopeResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scopeResultReceiver, filter)
        }

        setContent {
            ProjectEntropyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val mainViewModel: MainViewModel = hiltViewModel()
                    val uiState by mainViewModel.uiState.collectAsState()

                    // 编辑状态管理
                    var editingConfigId by remember { mutableStateOf<String?>(null) }

                    // 主界面
                    MainScreen(
                        uiState = uiState,
                        onCheckPermissions = { mainViewModel.checkPermissions() },
                        onOpenAccessibilitySettings = { mainViewModel.openAccessibilitySettings() },
                        onOpenOverlaySettings = { mainViewModel.openOverlaySettings() },
                        onSelectConfig = { mainViewModel.selectConfig(it) },
                        onEditConfig = { config ->
                            editingConfigId = config.id
                        },
                        onDeleteConfig = { mainViewModel.deleteConfig(it) },
                        onCreateConfig = {
                            val newConfig = mainViewModel.createNewConfig()
                            editingConfigId = newConfig.id
                        },
                        onStartFloatingBall = { mainViewModel.startFloatingBall() },
                        onStopFloatingBall = { mainViewModel.stopFloatingBall() }
                    )

                    // 底部弹窗编辑
                    if (editingConfigId != null) {
                        val editViewModel: ConfigEditViewModel = hiltViewModel(
                            key = editingConfigId
                        )
                        val config by editViewModel.config.collectAsState()
                        val isSaved by editViewModel.isSaved.collectAsState()

                        // 初始化时加载配置
                        androidx.compose.runtime.LaunchedEffect(editingConfigId) {
                            editViewModel.loadConfigById(editingConfigId!!)
                        }

                        // 保存成功后关闭
                        if (isSaved) {
                            editingConfigId = null
                            mainViewModel.refreshConfigs()
                        }

                        // 设置瞄准镜结果回调
                        scopeResultCallback = { xRatio, yRatio, jitterRadius ->
                            editViewModel.updateFromScopeResult(xRatio, yRatio, jitterRadius)
                        }

                        ConfigEditBottomSheet(
                            config = config,
                            onDismiss = { editingConfigId = null },
                            onSave = { editViewModel.saveConfig() },
                            onUpdateName = { editViewModel.updateName(it) },
                            onUpdateStylePreset = { editViewModel.updateStylePreset(it) },
                            onOpenScopeSetup = {
                                val intent = Intent(this@MainActivity, OverlaySetupService::class.java).apply {
                                    putExtra(OverlaySetupService.EXTRA_X_RATIO, config.likeAnchorXRatio)
                                    putExtra(OverlaySetupService.EXTRA_Y_RATIO, config.likeAnchorYRatio)
                                    putExtra(OverlaySetupService.EXTRA_JITTER_RADIUS, config.likeJitterRadius)
                                }
                                startService(intent)
                            },
                            onUpdateEnableCenterTap = { editViewModel.updateEnableCenterTap(it) },
                            onUpdateCenterFloatRangeX = { editViewModel.updateCenterFloatRangeX(it) },
                            onUpdateCenterFloatRangeY = { editViewModel.updateCenterFloatRangeY(it) },
                            onUpdateReactionTimeMin = { editViewModel.updateReactionTimeMin(it) },
                            onUpdateReactionTimeMax = { editViewModel.updateReactionTimeMax(it) },
                            onUpdateLikeAnchorXRatio = { editViewModel.updateLikeAnchorXRatio(it) },
                            onUpdateLikeAnchorYRatio = { editViewModel.updateLikeAnchorYRatio(it) },
                            onUpdateLikeJitterRadius = { editViewModel.updateLikeJitterRadius(it) },
                            onUpdateBurstIntervalMin = { editViewModel.updateBurstIntervalMin(it) },
                            onUpdateBurstIntervalMax = { editViewModel.updateBurstIntervalMax(it) },
                            onUpdatePauseIntervalMin = { editViewModel.updatePauseIntervalMin(it) },
                            onUpdatePauseIntervalMax = { editViewModel.updatePauseIntervalMax(it) },
                            onUpdatePauseProbability = { editViewModel.updatePauseProbability(it) }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回应用时重新检查权限
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(scopeResultReceiver)
    }
}
