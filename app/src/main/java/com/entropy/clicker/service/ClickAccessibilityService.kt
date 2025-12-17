package com.entropy.clicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.entropy.clicker.data.model.BallState
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.data.model.ClickerState
import com.entropy.clicker.domain.clicker.HumanClicker

/**
 * 无障碍服务 - 负责执行模拟点击
 */
class ClickAccessibilityService : AccessibilityService() {

    companion object {
        // 服务实例引用（用于与悬浮球通信）
        var instance: ClickAccessibilityService? = null
            private set

        // 广播动作
        const val ACTION_STATE_CHANGED = "com.entropy.clicker.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val EXTRA_CLICK_COUNT = "click_count"
    }

    private lateinit var humanClicker: HumanClicker
    private val handler = Handler(Looper.getMainLooper())

    // 状态
    private var state: ClickerState = ClickerState.IDLE
    private var currentConfig: ClickConfig? = null
    private var clickCount = 0
    private var startTime: Long? = null

    // 回调
    var onStateChanged: ((BallState, Int) -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        humanClicker = HumanClicker(resources.displayMetrics)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不处理任何事件
    }

    override fun onInterrupt() {
        // 服务中断时停止点击
        stopClicking()
    }

    // ========== 控制接口 ==========

    /**
     * 开始点击
     */
    fun startClicking(config: ClickConfig) {
        if (state.isRunning) return

        currentConfig = config
        humanClicker.updateConfig(config)
        clickCount = 0
        startTime = System.currentTimeMillis()

        // 根据配置决定是否从中心点击开始
        if (config.enableCenterTap) {
            updateState(ClickerState.Status.CENTER_TAP)
            performCenterTap()
        } else {
            updateState(ClickerState.Status.LIKE_BURST)
            performLikeLoop()
        }
    }

    /**
     * 暂停点击
     */
    fun pauseClicking() {
        if (!state.isRunning) return
        handler.removeCallbacksAndMessages(null)
        updateState(ClickerState.Status.PAUSED)
    }

    /**
     * 恢复点击
     */
    fun resumeClicking() {
        if (state.status != ClickerState.Status.PAUSED) return

        // 如果配置已更新，应用新配置
        currentConfig?.let { humanClicker.updateConfig(it) }

        updateState(ClickerState.Status.LIKE_BURST)
        performLikeLoop()
    }

    /**
     * 停止点击
     */
    fun stopClicking() {
        handler.removeCallbacksAndMessages(null)
        clickCount = 0
        startTime = null
        updateState(ClickerState.Status.IDLE)
    }

    /**
     * 更新配置（热更新）
     */
    fun updateConfig(config: ClickConfig) {
        currentConfig = config
        // 如果当前是暂停状态，等恢复时才应用新配置
        // 如果正在运行，立即应用（但不重置锚点）
        if (state.isRunning) {
            humanClicker.updateConfig(config)
        }
    }

    /**
     * 切换状态（启动/暂停）
     */
    fun toggle(config: ClickConfig) {
        when (state.status) {
            ClickerState.Status.IDLE -> startClicking(config)
            ClickerState.Status.PAUSED -> {
                currentConfig = config
                resumeClicking()
            }
            else -> pauseClicking()
        }
    }

    // ========== 点击执行 ==========

    /**
     * 执行中心激活点击
     */
    private fun performCenterTap() {
        if (state.status != ClickerState.Status.CENTER_TAP) return

        val path = humanClicker.getCenterClickPath()
        val duration = humanClicker.getCenterPressDuration()

        dispatchClick(path, duration) {
            // 中心点击完成后，进入反应等待阶段
            updateState(ClickerState.Status.REACTION_WAIT)
            val reactionTime = humanClicker.getReactionTime()
            handler.postDelayed({
                if (state.status == ClickerState.Status.REACTION_WAIT) {
                    updateState(ClickerState.Status.LIKE_BURST)
                    performLikeLoop()
                }
            }, reactionTime)
        }
    }

    /**
     * 执行点赞循环点击
     */
    private fun performLikeLoop() {
        if (state.status != ClickerState.Status.LIKE_BURST &&
            state.status != ClickerState.Status.LIKE_PAUSE) return

        // 检查是否应该停止
        val runDuration = startTime?.let { System.currentTimeMillis() - it } ?: 0
        if (humanClicker.shouldStop(clickCount, runDuration)) {
            stopClicking()
            return
        }

        val path = humanClicker.getLikeClickPath()
        val duration = humanClicker.getPressDuration()

        dispatchClick(path, duration) {
            clickCount++
            notifyStateChanged()

            // 计算下一次点击间隔
            scheduleNextLike()
        }
    }

    /**
     * 调度下一次点赞点击
     */
    private fun scheduleNextLike() {
        if (!state.isRunning) return

        val interval = humanClicker.getNextClickInterval()

        // 根据间隔长度判断是爆发还是停顿状态
        val config = currentConfig ?: return
        val newStatus = if (interval >= config.pauseIntervalMin) {
            ClickerState.Status.LIKE_PAUSE
        } else {
            ClickerState.Status.LIKE_BURST
        }

        if (state.status != newStatus) {
            updateState(newStatus)
        }

        handler.postDelayed({
            performLikeLoop()
        }, interval)
    }

    /**
     * 分发点击手势
     */
    private fun dispatchClick(path: Path, duration: Long, onComplete: () -> Unit) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                // 手势被取消，尝试重新执行
                if (state.isRunning) {
                    handler.postDelayed({ onComplete() }, 100)
                }
            }
        }, handler)
    }

    // ========== 状态管理 ==========

    private fun updateState(status: ClickerState.Status) {
        state = state.copy(
            status = status,
            currentConfig = currentConfig,
            clickCount = clickCount,
            startTime = startTime
        )
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        val ballState = when (state.status) {
            ClickerState.Status.IDLE -> BallState.IDLE
            ClickerState.Status.PAUSED -> BallState.PAUSED
            else -> BallState.RUNNING
        }
        onStateChanged?.invoke(ballState, clickCount)

        // 发送广播
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, ballState.name)
            putExtra(EXTRA_CLICK_COUNT, clickCount)
            setPackage(packageName)
        })
    }

    fun getCurrentState(): ClickerState = state
    fun getClickCount(): Int = clickCount
}
