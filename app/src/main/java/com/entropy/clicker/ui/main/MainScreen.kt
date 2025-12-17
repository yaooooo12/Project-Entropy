package com.entropy.clicker.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.entropy.clicker.R
import com.entropy.clicker.data.model.ClickConfig
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    // 检查权限
    LaunchedEffect(Unit) {
        onCheckPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Entropy") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (uiState.isAccessibilityEnabled && uiState.isOverlayEnabled) {
                FloatingActionButton(
                    onClick = {
                        if (uiState.isFloatingBallRunning) {
                            onStopFloatingBall()
                        } else {
                            onStartFloatingBall()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.isFloatingBallRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isFloatingBallRunning) "停止" else "启动"
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限提示卡片
            if (!uiState.isAccessibilityEnabled || !uiState.isOverlayEnabled) {
                item {
                    PermissionCard(
                        isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                        isOverlayEnabled = uiState.isOverlayEnabled,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenOverlaySettings = onOpenOverlaySettings
                    )
                }
            }

            // 当前配置
            item {
                CurrentConfigCard(
                    config = uiState.currentConfig,
                    onEdit = { uiState.currentConfig?.let { onEditConfig(it) } }
                )
            }

            // 操作按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCreateConfig,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("新建配置")
                    }
                }
            }

            // 配置列表标题
            item {
                Text(
                    text = "配置列表",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // 配置列表
            items(uiState.configs) { config ->
                ConfigListItem(
                    config = config,
                    isSelected = config.id == uiState.currentConfig?.id,
                    onSelect = { onSelectConfig(config) },
                    onEdit = { onEditConfig(config) },
                    onDelete = { onDeleteConfig(config) }
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isAccessibilityEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccessibilitySettings() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.accessibility_not_enabled),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.click_to_enable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            if (!isOverlayEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOverlaySettings() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.overlay_not_enabled),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.click_to_enable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun CurrentConfigCard(
    config: ClickConfig?,
    onEdit: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.current_config),
                    style = MaterialTheme.typography.titleMedium
                )
                if (config != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (config != null) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "点赞区域: (${(config.likeAnchorXRatio * 100).toInt()}%, ${(config.likeAnchorYRatio * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "点击节奏: ${config.burstIntervalMin}-${config.burstIntervalMax}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "最后修改: ${formatTime(config.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            } else {
                Text(
                    text = "暂无配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ConfigListItem(
    config: ClickConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTime(config.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除配置") },
            text = { Text("确定要删除配置 \"${config.name}\" 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
