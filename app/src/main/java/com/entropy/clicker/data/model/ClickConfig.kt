package com.entropy.clicker.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 点击配置数据模型
 * 所有像素值参数均基于 1080 x 1920 基准屏幕，运行时会根据实际屏幕尺寸自动缩放
 */
@Serializable
data class ClickConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "默认配置",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // ========== 中心激活点击 ==========
    val enableCenterTap: Boolean = true,           // 是否启用中心激活
    val centerFloatRangeX: Int = 200,              // X轴浮动范围 (基准像素，自动缩放)
    val centerFloatRangeY: Int = 400,              // Y轴浮动范围 (基准像素，自动缩放)
    val centerTapExtraDuration: Long = 50,         // 额外按压时长 (ms)

    // ========== 反应等待 ==========
    val reactionTimeMin: Long = 800,               // 最小反应时间 (ms)
    val reactionTimeMax: Long = 1800,              // 最大反应时间 (ms)

    // ========== 点赞区域 ==========
    val likeAnchorXRatio: Float = 0.85f,           // 锚点X位置 (屏幕比例，无需缩放)
    val likeAnchorYRatio: Float = 0.80f,           // 锚点Y位置 (屏幕比例，无需缩放)
    val likeJitterRadius: Int = 50,                // 抖动半径 (基准像素，自动缩放)

    // ========== 漂移参数 ==========
    val driftProbability: Float = 0.1f,            // 漂移概率 (0-1)
    val driftRange: Int = 5,                       // 漂移范围 (基准像素，自动缩放)

    // ========== 点击节奏 ==========
    val burstIntervalMin: Long = 60,               // 爆发模式最小间隔 (ms)
    val burstIntervalMax: Long = 150,              // 爆发模式最大间隔 (ms)
    val pauseIntervalMin: Long = 300,              // 停顿最小间隔 (ms)
    val pauseIntervalMax: Long = 600,              // 停顿最大间隔 (ms)
    val pauseProbability: Float = 0.1f,            // 停顿概率 (0-1)

    // ========== 按压时长 ==========
    val pressDurationBase: Long = 40,              // 基础按压时长 (ms)
    val pressDurationVariance: Int = 20,           // 按压时长方差

    // ========== 运行限制 ==========
    val maxClickCount: Int = 0,                    // 最大点击次数 (0=无限)
    val maxRunDuration: Long = 0,                  // 最大运行时长 (0=无限, ms)

    // ========== 微滑动 ==========
    val microSlideMin: Int = 2,                    // 微滑动最小距离 (基准像素，自动缩放)
    val microSlideMax: Int = 5                     // 微滑动最大距离 (基准像素，自动缩放)
) {
    companion object {
        val DEFAULT = ClickConfig()
    }
}
