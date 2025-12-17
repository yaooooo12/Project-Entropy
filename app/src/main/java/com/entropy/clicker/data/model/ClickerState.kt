package com.entropy.clicker.data.model

/**
 * 点击器运行状态
 */
data class ClickerState(
    val status: Status = Status.IDLE,
    val currentConfig: ClickConfig? = null,
    val clickCount: Int = 0,
    val startTime: Long? = null,
    val lastClickTime: Long? = null
) {
    enum class Status {
        IDLE,           // 空闲
        CENTER_TAP,     // 执行中心点击
        REACTION_WAIT,  // 反应等待
        LIKE_BURST,     // 爆发点击
        LIKE_PAUSE,     // 微停顿
        PAUSED          // 用户暂停
    }

    val isRunning: Boolean
        get() = status != Status.IDLE && status != Status.PAUSED

    val runDuration: Long
        get() = startTime?.let { System.currentTimeMillis() - it } ?: 0

    companion object {
        val IDLE = ClickerState()
    }
}

/**
 * 悬浮球状态
 */
enum class BallState {
    IDLE,       // 空闲 - 灰色
    RUNNING,    // 运行中 - 绿色
    PAUSED      // 已暂停 - 黄色
}
