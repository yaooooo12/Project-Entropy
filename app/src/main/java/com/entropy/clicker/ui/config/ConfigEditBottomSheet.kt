package com.entropy.clicker.ui.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.entropy.clicker.R
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.data.model.ClickStylePreset
import com.entropy.clicker.ui.components.ClickStyleSelector

/**
 * 配置编辑底部弹窗
 * 简化的编辑界面，高级参数折叠显示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditBottomSheet(
    config: ClickConfig,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateStylePreset: (ClickStylePreset) -> Unit,
    onOpenScopeSetup: () -> Unit,
    // 高级参数回调
    onUpdateEnableCenterTap: (Boolean) -> Unit,
    onUpdateCenterFloatRangeX: (Int) -> Unit,
    onUpdateCenterFloatRangeY: (Int) -> Unit,
    onUpdateReactionTimeMin: (Long) -> Unit,
    onUpdateReactionTimeMax: (Long) -> Unit,
    onUpdateLikeAnchorXRatio: (Float) -> Unit,
    onUpdateLikeAnchorYRatio: (Float) -> Unit,
    onUpdateLikeJitterRadius: (Int) -> Unit,
    onUpdateBurstIntervalMin: (Long) -> Unit,
    onUpdateBurstIntervalMax: (Long) -> Unit,
    onUpdatePauseIntervalMin: (Long) -> Unit,
    onUpdatePauseIntervalMax: (Long) -> Unit,
    onUpdatePauseProbability: (Float) -> Unit
) {
    var showAdvancedParams by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编辑配置",
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存")
                }
            }

            // 配置名称
            OutlinedTextField(
                value = config.name,
                onValueChange = onUpdateName,
                label = { Text(stringResource(R.string.config_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 目标区域
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "目标区域",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (config.positionSetByScope) "瞄准镜已设置" else "手动设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "位置: ${(config.likeAnchorXRatio * 100).toInt()}%, ${(config.likeAnchorYRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledTonalButton(onClick = onOpenScopeSetup) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("选取")
                    }
                }
            }

            // 点击风格
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "点击风格",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(12.dp))
                    ClickStyleSelector(
                        selected = config.stylePreset,
                        onSelect = onUpdateStylePreset
                    )
                }
            }

            // 高级参数折叠
            Card(
                onClick = { showAdvancedParams = !showAdvancedParams }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "高级参数",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Icon(
                        imageVector = if (showAdvancedParams) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showAdvancedParams) "收起" else "展开"
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedParams) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 中心激活
                    SectionCard(title = stringResource(R.string.center_tap_section)) {
                        SwitchRow(
                            label = stringResource(R.string.enable_center_tap),
                            checked = config.enableCenterTap,
                            onCheckedChange = onUpdateEnableCenterTap
                        )
                        if (config.enableCenterTap) {
                            SliderRow(
                                label = stringResource(R.string.x_float_range),
                                value = config.centerFloatRangeX.toFloat(),
                                onValueChange = { onUpdateCenterFloatRangeX(it.toInt()) },
                                valueRange = 50f..500f,
                                unit = "px"
                            )
                            SliderRow(
                                label = stringResource(R.string.y_float_range),
                                value = config.centerFloatRangeY.toFloat(),
                                onValueChange = { onUpdateCenterFloatRangeY(it.toInt()) },
                                valueRange = 100f..800f,
                                unit = "px"
                            )
                        }
                    }

                    // 反应时间
                    SectionCard(title = stringResource(R.string.reaction_time_section)) {
                        SliderRow(
                            label = stringResource(R.string.min_reaction_time),
                            value = config.reactionTimeMin.toFloat(),
                            onValueChange = { onUpdateReactionTimeMin(it.toLong()) },
                            valueRange = 200f..2000f,
                            unit = "ms"
                        )
                        SliderRow(
                            label = stringResource(R.string.max_reaction_time),
                            value = config.reactionTimeMax.toFloat(),
                            onValueChange = { onUpdateReactionTimeMax(it.toLong()) },
                            valueRange = 500f..5000f,
                            unit = "ms"
                        )
                    }

                    // 点赞区域手动设置
                    SectionCard(title = stringResource(R.string.like_area_section)) {
                        SliderRow(
                            label = stringResource(R.string.anchor_x_ratio),
                            value = config.likeAnchorXRatio,
                            onValueChange = onUpdateLikeAnchorXRatio,
                            valueRange = 0.5f..1f,
                            unit = "%",
                            displayMultiplier = 100f
                        )
                        SliderRow(
                            label = stringResource(R.string.anchor_y_ratio),
                            value = config.likeAnchorYRatio,
                            onValueChange = onUpdateLikeAnchorYRatio,
                            valueRange = 0.5f..1f,
                            unit = "%",
                            displayMultiplier = 100f
                        )
                        SliderRow(
                            label = stringResource(R.string.jitter_radius),
                            value = config.likeJitterRadius.toFloat(),
                            onValueChange = { onUpdateLikeJitterRadius(it.toInt()) },
                            valueRange = 10f..150f,
                            unit = "px"
                        )
                    }

                    // 点击节奏自定义
                    SectionCard(title = stringResource(R.string.click_rhythm_section)) {
                        Text(
                            text = "爆发间隔",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        SliderRow(
                            label = "最小",
                            value = config.burstIntervalMin.toFloat(),
                            onValueChange = { onUpdateBurstIntervalMin(it.toLong()) },
                            valueRange = 30f..200f,
                            unit = "ms"
                        )
                        SliderRow(
                            label = "最大",
                            value = config.burstIntervalMax.toFloat(),
                            onValueChange = { onUpdateBurstIntervalMax(it.toLong()) },
                            valueRange = 50f..300f,
                            unit = "ms"
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "停顿间隔",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        SliderRow(
                            label = "最小",
                            value = config.pauseIntervalMin.toFloat(),
                            onValueChange = { onUpdatePauseIntervalMin(it.toLong()) },
                            valueRange = 100f..1000f,
                            unit = "ms"
                        )
                        SliderRow(
                            label = "最大",
                            value = config.pauseIntervalMax.toFloat(),
                            onValueChange = { onUpdatePauseIntervalMax(it.toLong()) },
                            valueRange = 200f..2000f,
                            unit = "ms"
                        )

                        SliderRow(
                            label = stringResource(R.string.pause_probability),
                            value = config.pauseProbability,
                            onValueChange = onUpdatePauseProbability,
                            valueRange = 0f..0.5f,
                            unit = "%",
                            displayMultiplier = 100f
                        )
                    }
                }
            }
        }
    }
}
