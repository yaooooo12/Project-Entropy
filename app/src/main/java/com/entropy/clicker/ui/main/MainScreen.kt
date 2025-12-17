package com.entropy.clicker.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.ui.components.ConfigCoverCard
import com.entropy.clicker.ui.components.EmptyConfigCard
import com.entropy.clicker.ui.components.PulsingButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onCheckPermissions: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onSelectConfig: (ClickConfig) -> Unit,
    onEditConfig: (ClickConfig) -> Unit,
    onDeleteConfig: (ClickConfig) -> Unit,
    onCreateConfig: () -> Unit,
    onStartFloatingBall: () -> Unit,
    onStopFloatingBall: () -> Unit
) {
    // 监听生命周期，每次 onResume 时检查权限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onCheckPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsReady = uiState.isAccessibilityEnabled && uiState.isOverlayEnabled

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "ENTROPY",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                },
                actions = {
                    IconButton(onClick = onCreateConfig) {
                        Icon(Icons.Default.Add, contentDescription = "新建配置")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 权限提示区域
            if (!permissionsReady) {
                PermissionTaskCard(
                    isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                    isOverlayEnabled = uiState.isOverlayEnabled,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            // 配置卡片区域
            if (uiState.configs.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyConfigCard(onCreate = onCreateConfig)
                }
            } else {
                // HorizontalPager 配置卡片
                val pagerState = rememberPagerState(
                    initialPage = uiState.configs.indexOfFirst { it.id == uiState.currentConfig?.id }
                        .coerceAtLeast(0),
                    pageCount = { uiState.configs.size }
                )

                // 监听页面变化，更新当前选中配置
                LaunchedEffect(pagerState.currentPage) {
                    if (uiState.configs.isNotEmpty() && pagerState.currentPage < uiState.configs.size) {
                        val selectedConfig = uiState.configs[pagerState.currentPage]
                        if (selectedConfig.id != uiState.currentConfig?.id) {
                            onSelectConfig(selectedConfig)
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    val config = uiState.configs[page]
                    ConfigCoverCard(
                        config = config,
                        onEdit = { onEditConfig(config) }
                    )
                }

                // 页面指示器
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(uiState.configs.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Surface(
                            modifier = Modifier.size(if (isSelected) 10.dp else 8.dp),
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        ) {}
                    }
                }
            }

            // 启动按钮区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                PulsingButton(
                    text = if (uiState.isFloatingBallRunning) "停止" else "启动",
                    icon = if (uiState.isFloatingBallRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    onClick = {
                        if (uiState.isFloatingBallRunning) {
                            onStopFloatingBall()
                        } else {
                            onStartFloatingBall()
                        }
                    },
                    enabled = permissionsReady && uiState.currentConfig != null,
                    isActive = uiState.isFloatingBallRunning
                )
            }
        }
    }
}

/**
 * 权限任务卡片 - 游戏化引导风格
 */
@Composable
fun PermissionTaskCard(
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedCount = listOf(isAccessibilityEnabled, isOverlayEnabled).count { it }
    val totalCount = 2
    val progress = completedCount.toFloat() / totalCount

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "解锁任务",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                // 进度指示
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "$completedCount / $totalCount",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 进度条
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
            )

            // 无障碍权限
            PermissionTaskItem(
                title = "无障碍服务",
                description = "允许自动点击操作",
                isCompleted = isAccessibilityEnabled,
                onClick = onOpenAccessibilitySettings
            )

            // 悬浮窗权限
            PermissionTaskItem(
                title = "悬浮窗权限",
                description = "显示悬浮控制球",
                isCompleted = isOverlayEnabled,
                onClick = onOpenOverlaySettings
            )
        }
    }
}

@Composable
private fun PermissionTaskItem(
    title: String,
    description: String,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!isCompleted) onClick() },
        enabled = !isCompleted,
        shape = MaterialTheme.shapes.medium,
        color = if (isCompleted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isCompleted) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // 文字内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = if (isCompleted) "已完成" else description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
            }

            // 右侧箭头或勾选
            if (!isCompleted) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
