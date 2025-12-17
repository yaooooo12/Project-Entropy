package com.entropy.clicker.data.model

/**
 * 点击风格预设
 * 将复杂的时间参数简化为语义化档位
 */
enum class ClickStylePreset(
    val burstIntervalMin: Long,
    val burstIntervalMax: Long,
    val pauseIntervalMin: Long,
    val pauseIntervalMax: Long,
    val pauseProbability: Float,
    val displayName: String,
    val description: String,
    val icon: String
) {
    /**
     * 佛系模式 - 像在欣赏直播
     * 点击较慢，停顿较多
     */
    CASUAL(
        burstIntervalMin = 200,
        burstIntervalMax = 400,
        pauseIntervalMin = 500,
        pauseIntervalMax = 1000,
        pauseProbability = 0.3f,
        displayName = "佛系",
        description = "像在欣赏直播",
        icon = "🐢"
    ),

    /**
     * 拟人模式 - 像真爱粉在点赞（默认推荐）
     * 平衡的点击节奏
     */
    NATURAL(
        burstIntervalMin = 60,
        burstIntervalMax = 150,
        pauseIntervalMin = 300,
        pauseIntervalMax = 600,
        pauseProbability = 0.1f,
        displayName = "拟人",
        description = "像真爱粉在点赞",
        icon = "👤"
    ),

    /**
     * 狂暴模式 - 高风险
     * 极快点击，极少停顿
     */
    FRENZY(
        burstIntervalMin = 30,
        burstIntervalMax = 80,
        pauseIntervalMin = 100,
        pauseIntervalMax = 200,
        pauseProbability = 0.05f,
        displayName = "狂暴",
        description = "高风险模式",
        icon = "🚀"
    );

    companion object {
        val DEFAULT = NATURAL
    }
}
