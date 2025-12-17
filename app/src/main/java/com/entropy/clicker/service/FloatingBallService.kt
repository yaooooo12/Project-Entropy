package com.entropy.clicker.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.entropy.clicker.R
import com.entropy.clicker.data.model.BallState
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.LinkedList

/**
 * 灵动岛风格悬浮球服务
 */
class FloatingBallService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "entropy_floating_ball"

        const val ACTION_UPDATE_CONFIG = "com.entropy.clicker.UPDATE_CONFIG"
        const val EXTRA_CONFIG = "config"

        private var currentConfig: ClickConfig = ClickConfig.DEFAULT

        fun setConfig(config: ClickConfig) {
            currentConfig = config
        }
    }

    // 显示模式
    private enum class DisplayMode {
        COMPACT,        // 紧凑：只显示 E
        WORKING,        // 工作：显示 E + CPS
        EXPANDED        // 展开：显示控制按钮
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var ballContainer: LinearLayout
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var ballText: TextView
    private lateinit var cpsContainer: View
    private lateinit var cpsText: TextView
    private lateinit var waveIndicator: TextView
    private lateinit var controlsContainer: View
    private lateinit var btnPause: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnClose: ImageButton

    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var ballState: BallState = BallState.IDLE
    private var clickCount: Int = 0
    private var displayMode: DisplayMode = DisplayMode.COMPACT

    // CPS 计算
    private val clickTimestamps = LinkedList<Long>()
    private var currentCps = 0.0

    // 触摸状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastClickTime = 0L
    private var isMoving = false
    private var longPressRunnable: Runnable? = null

    // 自动收起
    private var collapseRunnable: Runnable? = null

    // 波形动画
    private val wavePatterns = arrayOf("|||", "|‖|", "‖|‖", "|‖|")
    private var waveIndex = 0
    private var waveRunnable: Runnable? = null

    // 状态更新广播接收器
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ClickAccessibilityService.ACTION_STATE_CHANGED) {
                val stateName = intent.getStringExtra(ClickAccessibilityService.EXTRA_STATE)
                val count = intent.getIntExtra(ClickAccessibilityService.EXTRA_CLICK_COUNT, 0)
                stateName?.let {
                    updateBallState(BallState.valueOf(it), count)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        createFloatingBall()
        createNotificationChannel()

        // 注册广播接收器
        val filter = IntentFilter(ClickAccessibilityService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }

        // 设置无障碍服务的状态回调
        ClickAccessibilityService.instance?.onStateChanged = { state, count ->
            serviceScope.launch {
                updateBallState(state, count)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)

        try {
            unregisterReceiver(stateReceiver)
        } catch (e: Exception) { }

        try {
            windowManager.removeView(floatingView)
        } catch (e: Exception) { }

        ClickAccessibilityService.instance?.onStateChanged = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingBall() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(48),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(70)
            y = resources.displayMetrics.heightPixels / 2
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        ballContainer = floatingView.findViewById(R.id.ball_container)
        ballText = floatingView.findViewById(R.id.ball_text)
        cpsContainer = floatingView.findViewById(R.id.cps_container)
        cpsText = floatingView.findViewById(R.id.cps_text)
        waveIndicator = floatingView.findViewById(R.id.wave_indicator)
        controlsContainer = floatingView.findViewById(R.id.controls_container)
        btnPause = floatingView.findViewById(R.id.btn_pause)
        btnSettings = floatingView.findViewById(R.id.btn_settings)
        btnClose = floatingView.findViewById(R.id.btn_close)

        // 设置按钮点击事件
        btnPause.setOnClickListener {
            vibrateLight()
            ClickAccessibilityService.instance?.toggle(currentConfig)
            collapseToWorkingMode()
        }

        btnSettings.setOnClickListener {
            vibrateLight()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            collapseToCompactMode()
        }

        btnClose.setOnClickListener {
            vibrateLight()
            ClickAccessibilityService.instance?.stopClicking()
            stopSelf()
        }

        // 设置触摸监听
        floatingView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        windowManager.addView(floatingView, layoutParams)
        updateBallUI()
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isMoving = false

                // 启动长按检测
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = Runnable {
                    if (!isMoving && displayMode != DisplayMode.EXPANDED) {
                        vibrateHeavy()
                        expandToControlsMode()
                    }
                }
                handler.postDelayed(longPressRunnable!!, 500)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (dx * dx + dy * dy > 100) {
                    isMoving = true
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }

                if (!isMoving) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 300) {
                        // 双击
                        onDoubleClick()
                    } else {
                        // 单击
                        onSingleClick()
                    }
                    lastClickTime = currentTime
                }
            }
        }
        return true
    }

    private fun onSingleClick() {
        // 如果展开状态，收起
        if (displayMode == DisplayMode.EXPANDED) {
            if (ballState == BallState.RUNNING) {
                collapseToWorkingMode()
            } else {
                collapseToCompactMode()
            }
            return
        }

        val service = ClickAccessibilityService.instance
        if (service == null) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }

        vibrateLight()
        service.toggle(currentConfig)
    }

    private fun onDoubleClick() {
        vibrateHeavy()
        ClickAccessibilityService.instance?.stopClicking()
        collapseToCompactMode()
    }

    private fun updateBallState(state: BallState, count: Int) {
        val oldState = ballState
        ballState = state

        // 更新点击计数用于 CPS 计算
        if (count > clickCount) {
            val now = System.currentTimeMillis()
            clickTimestamps.add(now)
            // 只保留最近 5 秒内的点击时间戳
            while (clickTimestamps.isNotEmpty() && now - clickTimestamps.first > 5000) {
                clickTimestamps.removeFirst()
            }
            // 计算 CPS
            if (clickTimestamps.size > 1) {
                val duration = (now - clickTimestamps.first) / 1000.0
                currentCps = if (duration > 0) clickTimestamps.size / duration else 0.0
            }
        }
        clickCount = count

        // 根据状态切换显示模式
        if (displayMode != DisplayMode.EXPANDED) {
            when (state) {
                BallState.RUNNING -> {
                    if (displayMode != DisplayMode.WORKING) {
                        expandToWorkingMode()
                    }
                }
                BallState.IDLE -> {
                    if (oldState != BallState.IDLE) {
                        collapseToCompactMode()
                    }
                }
                BallState.PAUSED -> {
                    // 保持当前模式
                }
            }
        }

        updateBallUI()
    }

    private fun updateBallUI() {
        // 更新背景
        val backgroundRes = when (ballState) {
            BallState.IDLE -> R.drawable.ball_idle
            BallState.RUNNING -> R.drawable.ball_running
            BallState.PAUSED -> R.drawable.ball_paused
        }
        ballContainer.setBackgroundResource(backgroundRes)

        // 更新主文字
        ballText.text = when {
            displayMode == DisplayMode.EXPANDED -> "E"
            ballState == BallState.IDLE -> "E"
            else -> if (clickCount > 999) "999+" else clickCount.toString()
        }

        // 更新 CPS 显示
        if (displayMode == DisplayMode.WORKING && ballState == BallState.RUNNING) {
            cpsText.text = String.format("%.1f", currentCps)
        }

        // 更新暂停按钮图标
        btnPause.setImageResource(
            if (ballState == BallState.RUNNING) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    // ========== 动画方法 ==========

    private fun expandToWorkingMode() {
        if (displayMode == DisplayMode.WORKING) return
        displayMode = DisplayMode.WORKING

        cpsContainer.visibility = View.VISIBLE
        controlsContainer.visibility = View.GONE

        // 启动波形动画
        startWaveAnimation()

        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun expandToControlsMode() {
        if (displayMode == DisplayMode.EXPANDED) return
        displayMode = DisplayMode.EXPANDED

        cpsContainer.visibility = View.GONE
        controlsContainer.visibility = View.VISIBLE

        // 停止波形动画
        stopWaveAnimation()

        windowManager.updateViewLayout(floatingView, layoutParams)

        // 5秒后自动收起
        scheduleAutoCollapse()
    }

    private fun collapseToWorkingMode() {
        cancelAutoCollapse()
        displayMode = DisplayMode.WORKING

        cpsContainer.visibility = View.VISIBLE
        controlsContainer.visibility = View.GONE

        startWaveAnimation()

        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun collapseToCompactMode() {
        cancelAutoCollapse()
        displayMode = DisplayMode.COMPACT

        cpsContainer.visibility = View.GONE
        controlsContainer.visibility = View.GONE

        stopWaveAnimation()
        clickTimestamps.clear()
        currentCps = 0.0

        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun scheduleAutoCollapse() {
        cancelAutoCollapse()
        collapseRunnable = Runnable {
            if (ballState == BallState.RUNNING) {
                collapseToWorkingMode()
            } else {
                collapseToCompactMode()
            }
        }
        handler.postDelayed(collapseRunnable!!, 5000)
    }

    private fun cancelAutoCollapse() {
        collapseRunnable?.let { handler.removeCallbacks(it) }
        collapseRunnable = null
    }

    // ========== 波形动画 ==========

    private fun startWaveAnimation() {
        stopWaveAnimation()
        waveRunnable = object : Runnable {
            override fun run() {
                waveIndex = (waveIndex + 1) % wavePatterns.size
                waveIndicator.text = wavePatterns[waveIndex]
                handler.postDelayed(this, 200)
            }
        }
        handler.post(waveRunnable!!)
    }

    private fun stopWaveAnimation() {
        waveRunnable?.let { handler.removeCallbacks(it) }
        waveRunnable = null
    }

    // ========== 触觉反馈 ==========

    private fun vibrateLight() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(20, 80))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(20)
            }
        }
    }

    private fun vibrateHeavy() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(50, 200))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(50)
            }
        }
    }

    // ========== 通知 ==========

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮球服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮球运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_ball_notification_title))
            .setContentText(getString(R.string.floating_ball_notification_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
