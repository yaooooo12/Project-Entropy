# Project Entropy

一款 Android 自动点击应用，采用拟人化点击算法，模拟真实人类操作行为。

## 功能特性

- **拟人化点击算法**
  - 高斯分布坐标抖动
  - 锚点漂移模拟手持抖动
  - 微滑动轨迹防检测
  - 爆发/停顿节奏模拟

- **可视化瞄准镜**
  - 拖拽定位目标区域
  - 双指缩放调整范围
  - 发光黄色圆环显示

- **灵动岛悬浮球**
  - 三种显示模式：紧凑/工作/展开
  - 实时 CPS（每秒点击数）显示
  - 单击切换运行状态
  - 长按展开控制面板

- **配置管理**
  - 多配置切换（HorizontalPager 卡片流）
  - 三种预设风格：休闲/自然/狂热
  - 高级参数自定义
  - 配置热更新（暂停状态可编辑）

- **游戏化权限引导**
  - 进度条任务引导
  - 无障碍服务权限
  - 悬浮窗权限

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **架构**: MVVM + Hilt 依赖注入
- **数据**: Room 数据库 + DataStore
- **服务**: AccessibilityService + 前台服务

## 系统要求

- Android 7.0 (API 24) 及以上
- 需要开启无障碍服务权限
- 需要开启悬浮窗权限

## 构建项目

```bash
# 克隆仓库
git clone https://github.com/yaooooo12/Project-Entropy.git

# 进入项目目录
cd Project-Entropy

# 构建 Debug 版本
./gradlew assembleDebug

# APK 输出路径
# app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. **安装应用**后，首次打开需要授予权限
2. 点击**无障碍服务**任务，跳转设置开启
3. 点击**悬浮窗权限**任务，跳转设置开启
4. 点击右上角 **+** 创建配置
5. 在编辑界面设置**目标区域**和**点击风格**
6. 点击**启动**按钮，悬浮球出现
7. 单击悬浮球开始/暂停点击
8. 长按悬浮球展开控制面板

## 项目结构

```
app/src/main/java/com/entropy/clicker/
├── app/                    # Application 类
├── data/
│   ├── model/              # 数据模型 (ClickConfig, ClickStylePreset)
│   └── repository/         # 数据仓库
├── domain/
│   └── clicker/            # 点击算法 (HumanClicker)
├── service/
│   ├── ClickAccessibilityService   # 无障碍服务
│   ├── FloatingBallService         # 悬浮球服务
│   └── OverlaySetupService         # 瞄准镜覆盖层
└── ui/
    ├── components/         # UI 组件
    ├── config/             # 配置编辑界面
    ├── main/               # 主界面
    └── theme/              # 主题配置
```

## 免责声明

本应用仅供学习和研究使用。请遵守相关法律法规及平台使用条款，不得用于任何违规用途。使用本应用造成的任何后果由使用者自行承担。

## License

MIT License
