# Project Entropy UI/UX 重构方案

> **版本**: v2.0
> **状态**: 待确认
> **核心理念**: 第一性原理 (Musk) + 直觉设计 (Jobs)

---

## 1. 问题分析

### 1.1 现有设计的问题

当前 ConfigEditScreen.kt 让用户手动配置 X/Y 坐标百分比：

**用户心智模型断裂：**

用户大脑（想点右下角）→ 翻译成百分比（X=85%）→ 输入手机 → 试错 → 重复

### 1.2 第一性原理分析

| 问题 | 本质 | 解决方案 |
|------|------|----------|
| 用户目的是什么？ | "告诉软件点这里" | 直接在屏幕上指定 |
| 什么是多余步骤？ | 坐标/百分比翻译 | 删除翻译层 |
| 最短路径是什么？ | 所见即所得 (WYSIWYG) | 覆盖层拖拽设置 |

---

## 2. 新版交互设计："The Overlay Setup"

### 2.1 配置流程对比

**旧流程（当前实现）：**
打开 App → 新建配置 → 估算坐标填数字 → 启动悬浮球 → 试运行 → 发现歪了 → 回 App 改数字

**新流程（重构后）：**
新建配置 → 点击"选取目标" → 自动跳转/透明化 → 拖动"瞄准镜"到点赞位 → 保存

### 2.2 核心组件设计

#### A. 瞄准镜 (The Scope) - 代替 X/Y 坐标

当用户点击"设置区域"时，Project Entropy 最小化，屏幕上出现半透明的瞄准镜叠加层。

**视觉设计：**
```
        ┌─────────────────────────┐
        │    (目标 App 画面)       │
        │                         │
        │         ╭───╮           │
        │        ╱ ╭─╮ ╲          │  ← 外圈：抖动范围 (可缩放)
        │       │  │●│  │         │  ← 中心点：锚点位置 (可拖动)
        │        ╲ ╰─╯ ╱          │
        │         ╰───╯           │
        │                         │
        └─────────────────────────┘
```

**交互方式：**
| 手势 | 作用 | 映射参数 |
|------|------|----------|
| 拖拽中心 | 移动锚点位置 | likeAnchorXRatio, likeAnchorYRatio |
| 双指捏合 | 调整抖动范围 | likeJitterRadius |
| 长按确认 | 保存并返回 | - |

**技术实现（复用现有 FloatingBallService 模式）：**
```kotlin
// 新增: OverlaySetupService.kt
class OverlaySetupService : Service() {
    // 使用现有的 WindowManager 全屏覆盖模式
    private fun createScopeOverlay() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
    }

    // 坐标转换（与现有 HumanClicker 兼容）
    private fun screenPositionToRatio(x: Float, y: Float): Pair<Float, Float> {
        return Pair(x / screenWidth, y / screenHeight)
    }
}
```

#### B. 节奏均衡器 (The Equalizer) - 代替毫秒数

**参数映射表（与现有 ClickConfig 兼容）：**

| 档位 | burstIntervalMin/Max | pauseIntervalMin/Max | pauseProbability | 文案 |
|------|----------------------|----------------------|------------------|------|
| 佛系 | 200-400ms | 500-1000ms | 0.3 | "像在欣赏直播" |
| 拟人 | 60-150ms | 300-600ms | 0.1 | "像真爱粉在点赞" (默认) |
| 狂暴 | 30-80ms | 100-200ms | 0.05 | "高风险模式" |

**代码实现：**
```kotlin
// 新增: ClickStylePreset.kt
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
    CASUAL(200, 400, 500, 1000, 0.3f, "佛系", "像在欣赏直播", "🐢"),
    NATURAL(60, 150, 300, 600, 0.1f, "拟人", "像真爱粉在点赞", "👤"),
    FRENZY(30, 80, 100, 200, 0.05f, "狂暴", "高风险模式", "🚀")
}

// 修改: ClickConfig.kt 新增字段
data class ClickConfig(
    // ... 现有字段保持不变 ...
    val stylePreset: ClickStylePreset = ClickStylePreset.NATURAL,
    val useCustomTiming: Boolean = false,  // true 时使用详细参数
    val positionSetByScope: Boolean = false
)
```

---

## 3. 重构后的 UI 架构

### 3.1 主界面 (MainScreen) - 极简重构

从列表视图改为卡片流 (HorizontalPager + ConfigCard)

**布局设计：**
```
┌─────────────────────────────────────────────┐
│              E N T R O P Y                  │
├─────────────────────────────────────────────┤
│    ┌─────────────────────────────────┐      │
│    │      ╭─────────────────────╮    │      │
│    │      │   🎵  TikTok 直播   │    │      │  ← 当前配置卡片
│    │      │   ─────────────────  │    │      │
│    │      │   👤 拟人模式        │    │      │
│    │      ╰─────────────────────╯    │      │
│    └─────────────────────────────────┘      │
│         ○ ○ ● ○                             │  ← 配置指示器
│    ┌─────────────────────────────────┐      │
│    │        ◉  启  动               │      │  ← 呼吸灯效果
│    └─────────────────────────────────┘      │
└─────────────────────────────────────────────┘
```

**代码变更（MainScreen.kt）：**
```kotlin
// 现有: LazyColumn + items
// 重构: HorizontalPager + ConfigCoverCard + PulsingLaunchButton
```

### 3.2 编辑界面 - 底部弹窗 (Bottom Sheet)

替代全屏表单，用户可以同时看到目标 App

**布局设计：**
```
┌─────────────────────────────────────┐
│         (目标 App 透明区域)          │
│              [ 🎯 ]                 │  ← 瞄准镜
├─────────────────────────────────────┤
│ ⚙️ 配置名称                         │
│ 目标区域    [ 🎯 重选位置 ]          │
│ 点击风格                            │
│ [🐢]     [👤]     [🚀]             │
│  佛系    拟人     狂暴              │
│ [ 更多高级参数 ▼ ]                  │
│        [ 保存并启动 ]               │
└─────────────────────────────────────┘
```

**代码变更（ConfigEditScreen.kt → ConfigEditBottomSheet.kt）：**
```kotlin
@Composable
fun ConfigEditBottomSheet(
    config: ClickConfig,
    onPositionSelect: () -> Unit,  // 触发瞄准镜
    onStyleSelect: (ClickStylePreset) -> Unit,
    onSave: () -> Unit,
    // 保留原有回调用于高级参数
) {
    var showAdvanced by remember { mutableStateOf(false) }

    ModalBottomSheet {
        // 简化界面 + 折叠的高级参数
    }
}
```

### 3.3 悬浮球 - 灵动岛化

**三种状态动画变化：**
```
静止态              工作态                  长按态
  ╭──╮            ╭────────────╮          ╭────────────────╮
  │E │    →      │ E  ≋≋ 12.3 │    →    │ ⏸  ⚙️  ✕      │
  ╰──╯            ╰────────────╯          ╰────────────────╯
 48dp                120dp                     180dp
```

**代码变更（FloatingBallService.kt）：**
```kotlin
// 现有: 56dp 固定圆形
// 重构: 动态尺寸 + ValueAnimator 动画

private fun animateToExpanded() {
    ValueAnimator.ofInt(dp(48), dp(120)).apply {
        addUpdateListener {
            layoutParams.width = it.animatedValue as Int
            windowManager.updateViewLayout(floatingView, layoutParams)
        }
        start()
    }
}
```

---

## 4. 交互细节优化

### 4.1 触觉反馈 (Haptics)
- 瞄准镜经过屏幕中心/边缘时给予轻微震动
- 启动运行时给予引擎启动感的重震动

### 4.2 权限引导游戏化
从红色警告卡片改为"解锁任务"形式

---

## 5. 实现计划

### 5.1 阶段划分

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| Phase 1 | 瞄准镜叠加层 | P0 |
| Phase 2 | 节奏均衡器 | P0 |
| Phase 3 | 主界面重构 | P1 |
| Phase 4 | 底部弹窗编辑 | P1 |
| Phase 5 | 悬浮球灵动化 | P2 |
| Phase 6 | 触觉反馈 | P2 |
| Phase 7 | 权限引导优化 | P2 |

### 5.2 文件变更清单

**新增文件：**
- data/model/ClickStylePreset.kt - 点击风格预设
- service/OverlaySetupService.kt - 瞄准镜覆盖层服务
- ui/components/ScopeView.kt - 瞄准镜组件
- ui/components/ClickStyleSelector.kt - 风格选择器
- ui/components/CpsWaveform.kt - CPS 波形图
- ui/components/PulsingButton.kt - 呼吸灯按钮
- util/HapticFeedbackManager.kt - 触觉反馈管理

**修改文件：**
- data/model/ClickConfig.kt - 添加 stylePreset 字段
- service/FloatingBallService.kt - 灵动岛化改造
- ui/main/MainScreen.kt - 主界面重构
- ui/config/ConfigEditScreen.kt - 改为 BottomSheet

### 5.3 兼容性保证

重构遵循渐进增强原则：
1. ClickConfig 保留所有现有字段，新增字段有默认值
2. 高级参数仍可在折叠区域手动调整
3. 原有的百分比配置方式作为 fallback

---

## 6. 设计理念总结

| 维度 | 旧设计 | 新设计 |
|------|--------|--------|
| 目标用户 | 工程师 | 普通用户 |
| 核心操作 | 填写参数 | 直接拖拽 |
| 位置设置 | X=85%, Y=80% | 瞄准镜指哪打哪 |
| 节奏设置 | 60-150ms | 佛系/拟人/狂暴 |
| 视觉反馈 | 静态圆球 | 灵动岛动画 |
| 权限引导 | 红色警告 | 游戏化任务 |

**核心价值：**
- First Principles (Musk): 移除坐标转换层，直接操作目标
- Intuition (Jobs): 用瞄准镜隐喻代替坐标，用风格代替毫秒

---

## 7. 待确认事项

- [ ] Phase 1-2 (瞄准镜 + 均衡器) 是否优先实现？
- [ ] 是否保留现有的详细参数编辑界面作为"高级模式"？
- [ ] 悬浮球灵动化的动画复杂度是否可接受？
- [ ] 是否需要配置的"应用截图"背景功能？

---

*文档状态: 待用户确认后开始实施*
