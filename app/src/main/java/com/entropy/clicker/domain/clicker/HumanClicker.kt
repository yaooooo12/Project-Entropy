package com.entropy.clicker.domain.clicker

import android.graphics.Path
import android.util.DisplayMetrics
import com.entropy.clicker.data.model.ClickConfig
import java.util.Random
import kotlin.math.abs

/**
 * 拟人化坐标生成器
 * 通过高斯分布、锚点漂移、坐标抖动和微滑动轨迹模拟真实人类点击行为
 */
class HumanClicker(private val metrics: DisplayMetrics) {

    companion object {
        // 基准屏幕尺寸 (用于自适应缩放)
        const val BASE_WIDTH = 1080f
        const val BASE_HEIGHT = 1920f
    }

    private val random = Random()

    // ========== 屏幕参数 ==========
    private val screenWidth = metrics.widthPixels
    private val screenHeight = metrics.heightPixels
    private val centerX = screenWidth / 2f
    private val centerY = screenHeight / 2f

    // ========== 缩放因子 ==========
    private val scaleX = screenWidth / BASE_WIDTH
    private val scaleY = screenHeight / BASE_HEIGHT
    private val scaleAvg = (scaleX + scaleY) / 2f

    // ========== 运行时状态 ==========
    private var currentLikeAnchorX = 0f
    private var currentLikeAnchorY = 0f
    private var currentConfig: ClickConfig = ClickConfig.DEFAULT

    /**
     * 更新配置并重置锚点
     */
    fun updateConfig(config: ClickConfig) {
        currentConfig = config
        resetAnchor()
    }

    /**
     * 重置点赞锚点位置
     */
    fun resetAnchor() {
        currentLikeAnchorX = screenWidth * currentConfig.likeAnchorXRatio
        currentLikeAnchorY = screenHeight * currentConfig.likeAnchorYRatio
    }

    // ========== 缩放方法 ==========

    /** 将基准X像素值转换为实际像素值 */
    private fun scaledX(basePx: Int): Float = basePx * scaleX

    /** 将基准Y像素值转换为实际像素值 */
    private fun scaledY(basePx: Int): Float = basePx * scaleY

    /** 将基准半径值转换为实际像素值 (使用平均缩放因子) */
    private fun scaledRadius(basePx: Int): Float = basePx * scaleAvg

    // ========== 点击路径生成 ==========

    /**
     * 获取中心激活点击路径
     * 使用高斯分布在屏幕中心附近生成点击坐标
     */
    fun getCenterClickPath(): Path {
        val path = Path()

        // 使用高斯分布生成偏移量
        // nextGaussian() 返回均值0.0，标准差1.0的数
        // 除以3使得99.7%的点落在浮动范围内
        val offsetX = random.nextGaussian() * (scaledX(currentConfig.centerFloatRangeX) / 3)
        val offsetY = random.nextGaussian() * (scaledY(currentConfig.centerFloatRangeY) / 3)

        val targetX = centerX + offsetX.toFloat()
        val targetY = centerY + offsetY.toFloat()

        // 构建微滑动轨迹 (防检测)
        path.moveTo(targetX, targetY)

        // 手指按下会有轻微位移，模拟自然滑动
        val slideX = randomInRange(
            scaledRadius(currentConfig.microSlideMin).toInt(),
            scaledRadius(currentConfig.microSlideMax).toInt()
        )
        val slideY = randomInRange(
            scaledRadius(currentConfig.microSlideMin).toInt(),
            scaledRadius(currentConfig.microSlideMax).toInt()
        )
        path.lineTo(targetX + slideX, targetY + slideY)

        return path
    }

    /**
     * 获取点赞点击路径
     * 包含锚点漂移和坐标抖动
     */
    fun getLikeClickPath(): Path {
        val path = Path()

        // 1. 锚点漂移 (Drift): 模拟手持手机时的姿势微调
        if (random.nextFloat() < currentConfig.driftProbability) {
            val driftAmount = scaledRadius(currentConfig.driftRange)
            currentLikeAnchorX += (random.nextGaussian() * driftAmount).toFloat()
            currentLikeAnchorY += (random.nextGaussian() * driftAmount).toFloat()

            // 确保锚点不会漂移出屏幕
            currentLikeAnchorX = currentLikeAnchorX.coerceIn(0f, screenWidth.toFloat())
            currentLikeAnchorY = currentLikeAnchorY.coerceIn(0f, screenHeight.toFloat())
        }

        // 2. 坐标抖动 (Jitter): 围绕锚点的随机落点
        val jitterRadius = scaledRadius(currentConfig.likeJitterRadius)
        val jitterX = random.nextGaussian() * jitterRadius
        val jitterY = random.nextGaussian() * jitterRadius

        val targetX = currentLikeAnchorX + jitterX.toFloat()
        val targetY = currentLikeAnchorY + jitterY.toFloat()

        // 构建微滑动轨迹
        path.moveTo(targetX, targetY)

        val slideX = randomInRange(
            scaledRadius(currentConfig.microSlideMin).toInt(),
            scaledRadius(currentConfig.microSlideMax).toInt()
        )
        val slideY = randomInRange(
            scaledRadius(currentConfig.microSlideMin).toInt(),
            scaledRadius(currentConfig.microSlideMax).toInt()
        )
        path.lineTo(targetX + slideX, targetY + slideY)

        return path
    }

    /**
     * 获取拟人化的按压时长
     * 使用高斯分布生成 40ms - 100ms 范围内的按压时长
     */
    fun getPressDuration(): Long {
        val base = currentConfig.pressDurationBase
        val variance = currentConfig.pressDurationVariance
        return (base + abs(random.nextGaussian() * variance)).toLong()
    }

    /**
     * 获取中心点击的按压时长 (稍长一些)
     */
    fun getCenterPressDuration(): Long {
        return getPressDuration() + currentConfig.centerTapExtraDuration
    }

    /**
     * 获取反应等待时间
     */
    fun getReactionTime(): Long {
        val min = currentConfig.reactionTimeMin
        val max = currentConfig.reactionTimeMax
        return min + random.nextLong(max - min + 1)
    }

    /**
     * 获取下一次点击的间隔时间
     * 实现爆发/停顿算法
     */
    fun getNextClickInterval(): Long {
        // 根据停顿概率决定是爆发还是停顿
        return if (random.nextFloat() < currentConfig.pauseProbability) {
            // 停顿: 较长间隔
            randomInRange(
                currentConfig.pauseIntervalMin.toInt(),
                currentConfig.pauseIntervalMax.toInt()
            ).toLong()
        } else {
            // 爆发: 快速间隔
            randomInRange(
                currentConfig.burstIntervalMin.toInt(),
                currentConfig.burstIntervalMax.toInt()
            ).toLong()
        }
    }

    /**
     * 检查是否应该停止 (基于限制条件)
     */
    fun shouldStop(clickCount: Int, runDuration: Long): Boolean {
        // 检查最大点击次数
        if (currentConfig.maxClickCount > 0 && clickCount >= currentConfig.maxClickCount) {
            return true
        }
        // 检查最大运行时长
        if (currentConfig.maxRunDuration > 0 && runDuration >= currentConfig.maxRunDuration) {
            return true
        }
        return false
    }

    // ========== 工具方法 ==========

    private fun randomInRange(min: Int, max: Int): Int {
        if (min >= max) return min
        return min + random.nextInt(max - min + 1)
    }

    private fun Random.nextLong(bound: Long): Long {
        if (bound <= 0) return 0
        return (nextDouble() * bound).toLong()
    }
}
