package com.entropy.clicker.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.entropy.clicker.ui.config.ConfigEditScreen
import com.entropy.clicker.ui.config.ConfigEditViewModel
import com.entropy.clicker.ui.theme.ProjectEntropyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProjectEntropyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        // 主界面
                        composable("main") {
                            val viewModel: MainViewModel = hiltViewModel()
                            val uiState by viewModel.uiState.collectAsState()

                            MainScreen(
                                uiState = uiState,
                                onCheckPermissions = { viewModel.checkPermissions() },
                                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                onOpenOverlaySettings = { viewModel.openOverlaySettings() },
                                onSelectConfig = { viewModel.selectConfig(it) },
                                onEditConfig = { config ->
                                    navController.navigate("edit/${config.id}")
                                },
                                onDeleteConfig = { viewModel.deleteConfig(it) },
                                onCreateConfig = {
                                    val newConfig = viewModel.createNewConfig()
                                    navController.navigate("edit/${newConfig.id}")
                                },
                                onStartFloatingBall = { viewModel.startFloatingBall() },
                                onStopFloatingBall = { viewModel.stopFloatingBall() }
                            )
                        }

                        // 配置编辑界面
                        composable(
                            route = "edit/{configId}",
                            arguments = listOf(
                                navArgument("configId") { type = NavType.StringType }
                            )
                        ) {
                            val viewModel: ConfigEditViewModel = hiltViewModel()
                            val config by viewModel.config.collectAsState()
                            val isSaved by viewModel.isSaved.collectAsState()

                            if (isSaved) {
                                navController.popBackStack()
                            }

                            ConfigEditScreen(
                                config = config,
                                onBack = { navController.popBackStack() },
                                onSave = { viewModel.saveConfig() },
                                onUpdateName = { viewModel.updateName(it) },
                                onUpdateEnableCenterTap = { viewModel.updateEnableCenterTap(it) },
                                onUpdateCenterFloatRangeX = { viewModel.updateCenterFloatRangeX(it) },
                                onUpdateCenterFloatRangeY = { viewModel.updateCenterFloatRangeY(it) },
                                onUpdateReactionTimeMin = { viewModel.updateReactionTimeMin(it) },
                                onUpdateReactionTimeMax = { viewModel.updateReactionTimeMax(it) },
                                onUpdateLikeAnchorXRatio = { viewModel.updateLikeAnchorXRatio(it) },
                                onUpdateLikeAnchorYRatio = { viewModel.updateLikeAnchorYRatio(it) },
                                onUpdateLikeJitterRadius = { viewModel.updateLikeJitterRadius(it) },
                                onUpdateBurstIntervalMin = { viewModel.updateBurstIntervalMin(it) },
                                onUpdateBurstIntervalMax = { viewModel.updateBurstIntervalMax(it) },
                                onUpdatePauseIntervalMin = { viewModel.updatePauseIntervalMin(it) },
                                onUpdatePauseIntervalMax = { viewModel.updatePauseIntervalMax(it) },
                                onUpdatePauseProbability = { viewModel.updatePauseProbability(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次返回应用时重新检查权限
    }
}
